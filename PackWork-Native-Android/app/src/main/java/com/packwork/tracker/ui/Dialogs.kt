package com.packwork.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packwork.tracker.data.*

sealed interface Modal {
    data class ItemForm(val itemId: String? = null) : Modal
    data class Restock(val itemId: String) : Modal
    data object Issue : Modal
    data class Return(val jobId: String) : Modal
    data class Payment(val jobId: String) : Modal
}

@Composable
private fun FormDialog(
    title: String, subtitle: String, confirmLabel: String?,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
    error: String?, content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(2.dp))
                content()
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { if (confirmLabel != null) Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ContextBox(title: String, body: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ModalHost(modal: Modal, vm: PackWorkViewModel, setModal: (Modal?) -> Unit) {
    val close = { setModal(null) }
    when (modal) {
        is Modal.ItemForm -> ItemDialog(vm, modal.itemId, close)
        is Modal.Restock -> RestockDialog(vm, modal.itemId, close)
        is Modal.Issue -> IssueDialog(vm, close, setModal)
        is Modal.Return -> ReturnDialog(vm, modal.jobId, close)
        is Modal.Payment -> PaymentDialog(vm, modal.jobId, close)
    }
}

@Composable
private fun ItemDialog(vm: PackWorkViewModel, itemId: String?, close: () -> Unit) {
    val existing = vm.store.items.find { it.id == itemId }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sku by remember { mutableStateOf(existing?.sku ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "piece") }
    var stock by remember { mutableStateOf((existing?.officeStock ?: 0).toString()) }
    var rate by remember { mutableStateOf(numStr(existing?.ratePerPackedUnit ?: 0.0)) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    val currency = vm.store.currency

    FormDialog(
        title = if (existing != null) "Edit item" else "Add an item",
        subtitle = "Set the stock unit and the rate paid for each packed unit.",
        confirmLabel = if (existing != null) "Save changes" else "Add item",
        error = error, onDismiss = close,
        onConfirm = {
            val st = stock.toIntOrNull()
            val rt = rate.toDoubleOrNull()
            error = when {
                st == null -> "Enter the Office stock quantity."
                rt == null -> "Enter the rate per packed unit."
                else -> vm.saveItem(itemId, name, sku, unit, st, rt, active)
            }
            if (error == null) close()
        },
    ) {
        TextInput("Item name", name, { name = it }, placeholder = "e.g. Cotton tote bags")
        TextInput("SKU / reference (optional)", sku, { sku = it }, placeholder = "e.g. TB-04")
        TextInput("Count unit", unit, { unit = it }, placeholder = "piece, box, bundle")
        IntInput(if (existing != null) "Office Godown stock (${existing.unit})" else "Starting Office Godown stock", stock, { stock = it })
        DecimalInput("Rate per packed ${existing?.unit ?: "unit"} ($currency)", rate, { rate = it })
        Text(
            if (existing != null) "Changing Office stock records a dated adjustment. Home stock is calculated from tickets. Past tickets keep their saved name, unit, and rate."
            else "Starting Office stock is recorded as received today. Use Receive stock for later deliveries.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().clickable { active = !active }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = active, onCheckedChange = { active = it })
            Text("Item is active and available for new tickets", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RestockDialog(vm: PackWorkViewModel, itemId: String, close: () -> Unit) {
    val item = vm.store.items.find { it.id == itemId }
    if (item == null) { close(); return }
    var qty by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FormDialog(
        title = "Receive stock into Office",
        subtitle = "Add new ${item.unit} to ${item.name}; the date is kept for reports.",
        confirmLabel = "Receive stock", error = error, onDismiss = close,
        onConfirm = {
            error = vm.receiveStock(itemId, qty.toIntOrNull() ?: 0, date, notes)
            if (error == null) close()
        },
    ) {
        ContextBox(item.name, "Current Office Godown stock: ${item.officeStock} ${item.unit}")
        IntInput("Quantity received (${item.unit})", qty, { qty = it })
        DateField("Received date", date, { date = it })
        TextInput("Notes (optional)", notes, { notes = it }, placeholder = "Supplier, delivery, or batch details")
    }
}

@Composable
private fun IssueDialog(vm: PackWorkViewModel, close: () -> Unit, setModal: (Modal?) -> Unit) {
    val s = vm.store
    val active = s.items.filter { it.active }
    val withStock = active.filter { it.officeStock > 0 }
    var selectedId by remember { mutableStateOf((withStock.firstOrNull() ?: active.firstOrNull())?.id ?: "") }
    var qty by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var due by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = active.find { it.id == selectedId }

    FormDialog(
        title = "Transfer Office to Home",
        subtitle = "Move an item from the Office Godown into your Home Godown.",
        confirmLabel = if (withStock.isNotEmpty()) "Transfer to Home" else null,
        error = error, onDismiss = close,
        onConfirm = {
            error = vm.issue(selectedId, qty.toIntOrNull() ?: 0, date, due, notes)
            if (error == null) close()
        },
    ) {
        when {
            s.items.isEmpty() -> {
                ContextBox("Add your first item", "Add an item, set a positive starting Office quantity, and enter its packing rate. Then open this transfer again.")
                FilledTonalButton(onClick = { setModal(Modal.ItemForm()) }) { Text("Add item first") }
            }
            active.isEmpty() -> ContextBox("No active items", "Activate an item and receive Office stock on the Stock tab before transferring.")
            withStock.isEmpty() -> ContextBox("No active items have Office stock", "Receive stock into Office on the Stock tab before transferring.")
            else -> {
                DropdownField(
                    label = "Item in Office Godown",
                    selectedText = selected?.name ?: "",
                    options = active,
                    optionLabel = { "${it.name} · ${if (it.officeStock > 0) "${it.officeStock} ${it.unit} in Office" else "No Office stock"}" },
                    optionEnabled = { it.officeStock > 0 },
                    onSelect = { selectedId = it.id },
                )
                if (selected != null) {
                    ContextBox("Packing rate for ${selected.name}", "${fmtMoney(selected.ratePerPackedUnit, s.currency)} per packed ${selected.unit} · saved on this ticket")
                }
                IntInput("Quantity transferred to Home", qty, { qty = it })
                DateField("Transfer date", date, { date = it })
                DateField("Expected Home → Office date (optional)", due, { due = it }, clearable = true)
                TextInput("Notes (optional)", notes, { notes = it }, singleLine = false, minLines = 2, placeholder = "Batch details, agreed deadline…")
            }
        }
    }
}

@Composable
private fun ReturnDialog(vm: PackWorkViewModel, jobId: String, close: () -> Unit) {
    val job = vm.store.jobs.find { it.id == jobId }
    if (job == null) { close(); return }
    var date by remember { mutableStateOf(today()) }
    var packed by remember { mutableStateOf("0") }
    var unused by remember { mutableStateOf("0") }
    var damaged by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FormDialog(
        title = "Transfer Home to Office",
        subtitle = "Move packed or unused stock back to Office, or reconcile damaged stock from Home.",
        confirmLabel = "Transfer to Office", error = error, onDismiss = close,
        onConfirm = {
            error = vm.reconcile(jobId, date, packed.toIntOrNull() ?: 0, unused.toIntOrNull() ?: 0, damaged.toIntOrNull() ?: 0, notes)
            if (error == null) close()
        },
    ) {
        ContextBox(job.itemNameSnapshot, "${job.open} ${job.unitSnapshot} available in Home on this ticket · ${job.packed} packed so far")
        DateField("Transfer / reconciliation date", date, { date = it })
        IntInput("Packed to Office", packed, { packed = it })
        IntInput("Unused to Office", unused, { unused = it })
        IntInput("Damaged at Home", damaged, { damaged = it })
        TextInput("Notes (optional)", notes, { notes = it }, singleLine = false, minLines = 2, placeholder = "Anything to note about this transfer")
        Text(
            "Packed + unused quantities move into Office. Damaged quantities leave Home stock but do not enter Office. The total cannot exceed the ${job.open} ${job.unitSnapshot} available on this ticket.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaymentDialog(vm: PackWorkViewModel, jobId: String, close: () -> Unit) {
    val job = vm.store.jobs.find { it.id == jobId }
    if (job == null) { close(); return }
    val currency = vm.store.currency
    val methods = listOf("", "Cash", "Bank transfer", "Cheque", "Other")
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var method by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FormDialog(
        title = "Record a payment",
        subtitle = "Log any payment received against the earned balance for this ticket.",
        confirmLabel = "Save payment", error = error, onDismiss = close,
        onConfirm = {
            val a = amount.toDoubleOrNull()
            error = if (a == null) "Enter a payment amount." else vm.pay(jobId, a, date, method, reference)
            if (error == null) close()
        },
    ) {
        ContextBox(job.itemNameSnapshot, "Earned ${fmtMoney(job.earned, currency)} · unpaid ${fmtMoney(job.owed, currency)}")
        DecimalInput("Payment amount ($currency)", amount, { amount = it })
        DateField("Paid date", date, { date = it })
        DropdownField(
            label = "Method (optional)", selectedText = method.ifBlank { "Choose method" },
            options = methods, optionLabel = { it.ifBlank { "Choose method" } }, onSelect = { method = it },
        )
        TextInput("Reference (optional)", reference, { reference = it }, placeholder = "Receipt or transfer reference")
    }
}
