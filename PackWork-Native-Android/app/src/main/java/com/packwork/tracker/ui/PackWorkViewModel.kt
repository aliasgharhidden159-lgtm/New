package com.packwork.tracker.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.packwork.tracker.data.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import kotlin.math.abs
import kotlin.math.max

class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

class PackWorkViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(File(app.filesDir, "packwork-data.json"))

    var store by mutableStateOf(repo.load())
        private set
    var confirm by mutableStateOf<ConfirmRequest?>(null)
        private set

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val notices = _notices.asSharedFlow()

    fun notify(msg: String) { _notices.tryEmit(msg) }

    private fun commit(t: (StoreData) -> StoreData) {
        store = t(store)
        repo.save(store.toJson())
    }

    fun dismissConfirm() { confirm = null }
    fun acceptConfirm() {
        val c = confirm
        confirm = null
        c?.onConfirm?.invoke()
    }

    // ---------- Settings / backup ----------

    fun setCurrency(code: String) = commit { it.copy(currency = code) }

    fun backupJson(): String = store.toJson(withMeta = true, pretty = true)

    fun requestRestore(text: String) {
        val parsed = StoreData.parseOrNull(text)
        if (parsed == null) {
            notify("That file is not a valid PackWork backup.")
            return
        }
        confirm = ConfirmRequest(
            "Restore this backup?",
            "It will replace the records currently saved on this device. Save a backup first if you want to keep both.",
            "Restore",
        ) {
            commit { parsed }
            notify("Backup restored successfully.")
        }
    }

    // ---------- Forms (return an error message, or null on success) ----------

    fun saveItem(id: String?, name: String, sku: String, unit: String, officeStock: Int, rate: Double, active: Boolean): String? {
        val n = name.trim()
        val u = unit.trim()
        if (n.isEmpty()) return "Enter an item name."
        if (u.isEmpty()) return "Enter a count unit."
        val stock = max(0, officeStock)
        val r = max(0.0, rate)
        val existing = id?.let { i -> store.items.find { it.id == i } }
        val item = Item(existing?.id ?: newId(), n, sku.trim(), u, stock, r, active)
        if (existing != null) {
            val diff = stock - existing.officeStock
            val adj = if (diff != 0) StockReceipt(
                newId(), item.id, item.name, item.unit, abs(diff), today(), Kind.ADJUSTMENT,
                if (diff > 0) "in" else "out",
                "Manual Office stock correction from ${existing.officeStock} to $stock.",
            ) else null
            commit { s ->
                s.copy(
                    items = s.items.map { if (it.id == existing.id) item else it },
                    stockReceipts = if (adj != null) listOf(adj) + s.stockReceipts else s.stockReceipts,
                )
            }
            notify("Item details and Office stock updated.")
        } else {
            val opening = StockReceipt(newId(), item.id, n, u, stock, today(), Kind.OPENING, null, "")
            commit { s ->
                s.copy(
                    items = s.items + item,
                    stockReceipts = if (stock > 0) listOf(opening) + s.stockReceipts else s.stockReceipts,
                )
            }
            notify("Item added to your catalog.")
        }
        return null
    }

    fun receiveStock(itemId: String, qty: Int, date: String, notes: String): String? {
        val item = store.items.find { it.id == itemId } ?: return "Item not found."
        if (qty < 1) return "Enter a quantity of at least 1."
        val receipt = StockReceipt(newId(), item.id, item.name, item.unit, qty, date.ifBlank { today() }, Kind.RESTOCK, null, notes.trim())
        commit { s ->
            s.copy(
                items = s.items.map { if (it.id == item.id) it.copy(officeStock = it.officeStock + qty) else it },
                stockReceipts = listOf(receipt) + s.stockReceipts,
            )
        }
        notify("$qty ${item.unit} received into the Office Godown and added to report history.")
        return null
    }

    fun issue(itemId: String, qty: Int, issuedAt: String, dueAt: String, notes: String): String? {
        val item = store.items.find { it.id == itemId } ?: return "Choose an item."
        if (qty < 1 || qty > item.officeStock) return "Check the quantity. It must be between 1 and ${item.officeStock} in the Office Godown."
        val job = Job(
            id = newId(), itemId = item.id, itemNameSnapshot = item.name, unitSnapshot = item.unit,
            issuedQty = qty, rateSnapshot = item.ratePerPackedUnit, issuedAt = issuedAt.ifBlank { today() },
            dueAt = dueAt.ifBlank { null }, notes = notes.trim(),
        )
        commit { s ->
            s.copy(
                items = s.items.map { if (it.id == item.id) it.copy(officeStock = it.officeStock - qty) else it },
                jobs = listOf(job) + s.jobs,
            )
        }
        notify("$qty ${item.unit} transferred from Office to Home. Ticket created.")
        return null
    }

    fun reconcile(jobId: String, date: String, packed: Int, unused: Int, damaged: Int, notes: String): String? {
        val job = store.jobs.find { it.id == jobId } ?: return "Ticket not found."
        val total = packed + unused + damaged
        if (total < 1 || total > job.open) {
            return "Transfer and damage quantities must total 1–${job.open} ${job.unitSnapshot}, the available Home Godown balance on this ticket."
        }
        val entry = ReturnEntry(newId(), date.ifBlank { today() }, packed, unused, damaged, notes.trim())
        val toOffice = packed + unused
        commit { s ->
            s.copy(
                items = s.items.map { if (it.id == job.itemId) it.copy(officeStock = it.officeStock + toOffice) else it },
                jobs = s.jobs.map { if (it.id == job.id) it.copy(returns = it.returns + entry) else it },
            )
        }
        notify("Home balance updated: $toOffice ${job.unitSnapshot} transferred to Office; $damaged damaged.")
        return null
    }

    fun pay(jobId: String, amount: Double, date: String, method: String, reference: String): String? {
        val job = store.jobs.find { it.id == jobId } ?: return "Ticket not found."
        val remaining = job.owed
        val amt = round2(amount)
        if (amt <= 0.0 || amt > remaining) return "Enter an amount up to ${fmtMoney(remaining, store.currency)}."
        val p = Payment(newId(), date.ifBlank { today() }, amt, method.ifBlank { null }, reference.trim().ifBlank { null })
        commit { s -> s.copy(jobs = s.jobs.map { if (it.id == job.id) it.copy(payments = it.payments + p) else it }) }
        notify("Payment recorded against this ticket.")
        return null
    }

    // ---------- Deletes (with the same safeguards as the web app) ----------

    fun requestDeleteItem(id: String) {
        val item = store.items.find { it.id == id } ?: return
        val jobs = store.jobs.filter { it.itemId == id }
        val receipts = store.stockReceipts.filter { it.itemId == id }
        val extra = if (jobs.isNotEmpty() || receipts.isNotEmpty())
            " This also permanently deletes ${jobs.size} transfer ticket(s), ${jobs.sumOf { it.returns.size }} reconciliation(s), " +
                "${jobs.sumOf { it.payments.size }} payment(s), and ${receipts.size} stock entry/entries linked to it."
        else ""
        confirm = ConfirmRequest("Delete “${item.name}”?", "This cannot be undone.$extra", "Delete") {
            commit { s ->
                s.copy(
                    items = s.items.filter { it.id != id },
                    jobs = s.jobs.filter { it.itemId != id },
                    stockReceipts = s.stockReceipts.filter { it.itemId != id },
                )
            }
            notify("“${item.name}” and its linked records were deleted.")
        }
    }

    fun requestDeleteReceipt(id: String) {
        val c = store.stockReceipts.find { it.id == id } ?: return
        val item = store.items.find { it.id == c.itemId }
        val out = c.kind == Kind.ADJUSTMENT && c.adjustmentDirection == "out"
        val w = if (out) c.quantity else -c.quantity
        if (item != null && w < 0 && item.officeStock < abs(w)) {
            notify("Cannot delete this entry: Office stock has only ${item.officeStock} ${item.unit} available. Correct or remove later Office-to-Home transfers first.")
            return
        }
        val what = when (c.kind) { Kind.OPENING -> "opening stock"; Kind.RESTOCK -> "stock receipt"; else -> "stock adjustment" }
        val extra = if (c.kind == Kind.ADJUSTMENT)
            " This reverses the ${if (out) "decrease" else "increase"} of ${c.quantity} ${c.unitSnapshot} in Office stock."
        else " This removes ${c.quantity} ${c.unitSnapshot} from Office stock."
        confirm = ConfirmRequest("Delete this $what entry?", "Dated ${fmtDate(c.receivedAt)}.$extra", "Delete") {
            commit { s ->
                s.copy(
                    items = if (item != null) s.items.map { if (it.id == item.id) it.copy(officeStock = max(0, it.officeStock + w)) else it } else s.items,
                    stockReceipts = s.stockReceipts.filter { it.id != id },
                )
            }
            notify("Stock entry deleted and Office balance updated.")
        }
    }

    fun requestDeleteJob(id: String) {
        val job = store.jobs.find { it.id == id } ?: return
        val back = job.issuedQty - job.returns.sumOf { it.packedQty + it.unusedQty }
        confirm = ConfirmRequest(
            "Delete ticket ${job.ticketLabel}?",
            "${job.itemNameSnapshot}: this also deletes ${job.returns.size} reconciliation(s) and ${job.payments.size} payment(s). " +
                "The ticket's remaining goods will be restored to Office stock (+$back ${job.unitSnapshot}); " +
                "its Home balance, earnings, and payments will be removed.",
            "Delete",
        ) {
            commit { s ->
                s.copy(
                    items = s.items.map { if (it.id == job.itemId) it.copy(officeStock = it.officeStock + back) else it },
                    jobs = s.jobs.filter { it.id != id },
                )
            }
            notify("Ticket and its linked transactions were deleted; Office stock was recalculated.")
        }
    }

    fun requestDeleteReturn(jobId: String, returnId: String) {
        val job = store.jobs.find { it.id == jobId } ?: return
        val r = job.returns.find { it.id == returnId } ?: return
        val item = store.items.find { it.id == job.itemId }
        val x = r.packedQty + r.unusedQty
        if (item != null && item.officeStock < x) {
            notify("Cannot delete this reconciliation: Office stock has only ${item.officeStock} ${item.unit}. Correct or remove later Office-to-Home transfers first.")
            return
        }
        confirm = ConfirmRequest(
            "Delete this reconciliation?",
            "${fmtDate(r.returnedAt)} Home reconciliation for ${job.itemNameSnapshot}. Home stock on the ticket will increase by " +
                "${r.packedQty + r.unusedQty + r.damagedQty} ${job.unitSnapshot}; Office stock will decrease by $x ${job.unitSnapshot}, " +
                "and earned pay will decrease by ${fmtMoney(r.packedQty * job.rateSnapshot, store.currency)}.",
            "Delete",
        ) {
            commit { s ->
                s.copy(
                    items = if (item != null) s.items.map { if (it.id == item.id) it.copy(officeStock = it.officeStock - x) else it } else s.items,
                    jobs = s.jobs.map { if (it.id == jobId) it.copy(returns = it.returns.filter { rr -> rr.id != returnId }) else it },
                )
            }
            notify("Reconciliation deleted; ticket balances and earnings were recalculated.")
        }
    }

    fun requestDeletePayment(jobId: String, paymentId: String) {
        val job = store.jobs.find { it.id == jobId } ?: return
        val p = job.payments.find { it.id == paymentId } ?: return
        confirm = ConfirmRequest(
            "Delete this payment?",
            "The ${fmtMoney(p.amount, store.currency)} payment dated ${fmtDate(p.paidAt)} will be removed from ticket ${job.ticketLabel}. The unpaid balance will increase by this amount.",
            "Delete",
        ) {
            commit { s ->
                s.copy(jobs = s.jobs.map { if (it.id == jobId) it.copy(payments = it.payments.filter { pp -> pp.id != paymentId }) else it })
            }
            notify("Payment deleted; the unpaid balance was recalculated.")
        }
    }
}
