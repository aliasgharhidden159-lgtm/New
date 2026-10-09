package com.packwork.tracker.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max

// ---------- Ticket math (identical rules to the web app) ----------

fun round2(v: Double): Double = Math.round((v + 1e-9) * 100.0) / 100.0

val Job.packed: Int get() = returns.sumOf { it.packedQty }
val Job.unusedTotal: Int get() = returns.sumOf { it.unusedQty }
val Job.damagedTotal: Int get() = returns.sumOf { it.damagedQty }
val Job.reconciled: Int get() = returns.sumOf { it.packedQty + it.unusedQty + it.damagedQty }
val Job.open: Int get() = max(0, issuedQty - reconciled)
val Job.earned: Double get() = round2(packed * rateSnapshot)
val Job.paid: Double get() = round2(payments.sumOf { it.amount })
val Job.owed: Double get() = max(0.0, earned - paid)
val Job.ticketLabel: String get() = id.takeLast(6).uppercase()

// ---------- Ids, dates, formatting ----------

fun newId(): String {
    val rand = java.lang.Long.toString((Math.random() * 2176782336L).toLong(), 36).padStart(6, '0')
    return java.lang.Long.toString(System.currentTimeMillis(), 36) + "-" + rand
}

fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun monthStart(): String {
    val c = Calendar.getInstance()
    c.set(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
}

fun fmtDate(s: String?): String {
    if (s.isNullOrBlank() || s.length < 10) return "—"
    return try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s.substring(0, 10))
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(d!!)
    } catch (e: Exception) {
        s
    }
}

private fun utcFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun dateToMillis(s: String): Long? = try { utcFormat().parse(s)?.time } catch (e: Exception) { null }
fun millisToDate(ms: Long): String = utcFormat().format(Date(ms))

fun fmtMoney(amount: Double, currency: String): String = try {
    val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
    nf.currency = Currency.getInstance(currency)
    nf.format(amount)
} catch (e: Exception) {
    "$currency ${"%.2f".format(Locale.US, amount)}"
}

fun fmtInt(n: Int): String = NumberFormat.getIntegerInstance().format(n.toLong())
fun fmtSigned(n: Int): String = when {
    n > 0 -> "+${fmtInt(n)}"
    n < 0 -> "−${fmtInt(abs(n))}"
    else -> "0"
}

/** Number as plain text, no trailing ".0" (used in forms and CSV). */
fun numStr(d: Double): String =
    if (d == Math.floor(d) && abs(d) < 1e15) d.toLong().toString() else d.toString()

// ---------- Report ----------

object Activity {
    const val ISSUE = "Office → Home transfer"
    const val RETURN = "Home → Office transfer"
    const val DAMAGE = "Home damage adjustment"
    const val PAYMENT = "Payment received"
    const val ADJUSTMENT = "Office stock adjustment"
    const val OPENING = "Opening stock"
    const val RESTOCK = "Stock received"
}

data class ReportRow(
    val id: String,
    val date: String,
    val activity: String,
    val itemName: String,
    val ticketLabel: String,
    val unit: String,
    val inQty: Int,
    val outQty: Int,
    val packedIn: Int,
    val unusedIn: Int,
    val damagedQty: Int,
    val earned: Double,
    val paid: Double,
    val stockAdjustment: Int,
)

data class ReportSummary(
    val inventoryOut: Int,
    val inventoryIn: Int,
    val homeToOffice: Int,
    val restockedIn: Int,
    val packedQty: Int,
    val unusedQty: Int,
    val damagedQty: Int,
    val earned: Double,
    val paid: Double,
    val stockAdjustments: Int,
    val officeStockNow: Int,
    val atHomeNow: Int,
)

data class Report(
    val itemName: String,
    val from: String,
    val to: String,
    val summary: ReportSummary,
    val rows: List<ReportRow>,
)

private fun inRange(d: String, from: String, to: String) = d >= from && d <= to

fun buildReport(store: StoreData, itemId: String, from: String, to: String): Report {
    val items = store.items.filter { it.id == itemId }
    val ids = items.map { it.id }.toSet()
    val jobs = store.jobs.filter { it.itemId in ids }
    val rows = ArrayList<ReportRow>()

    for (r in store.stockReceipts) {
        if (r.itemId !in ids || !inRange(r.receivedAt, from, to)) continue
        val adj = r.kind == Kind.ADJUSTMENT
        rows.add(
            ReportRow(
                id = "receipt-${r.id}", date = r.receivedAt,
                activity = if (adj) Activity.ADJUSTMENT else if (r.kind == Kind.OPENING) Activity.OPENING else Activity.RESTOCK,
                itemName = r.itemNameSnapshot, ticketLabel = "—", unit = r.unitSnapshot,
                inQty = if (adj) 0 else r.quantity, outQty = 0, packedIn = 0, unusedIn = 0, damagedQty = 0,
                earned = 0.0, paid = 0.0,
                stockAdjustment = if (adj) (if (r.adjustmentDirection == "out") -r.quantity else r.quantity) else 0,
            )
        )
    }
    for (j in jobs) {
        val label = "Ticket ${j.ticketLabel}"
        if (inRange(j.issuedAt, from, to)) {
            rows.add(
                ReportRow("issue-${j.id}", j.issuedAt, Activity.ISSUE, j.itemNameSnapshot, label, j.unitSnapshot,
                    0, j.issuedQty, 0, 0, 0, 0.0, 0.0, 0)
            )
        }
        for (r in j.returns) {
            if (!inRange(r.returnedAt, from, to)) continue
            rows.add(
                ReportRow(
                    "return-${j.id}-${r.id}", r.returnedAt,
                    if (r.packedQty + r.unusedQty > 0) Activity.RETURN else Activity.DAMAGE,
                    j.itemNameSnapshot, label, j.unitSnapshot,
                    r.packedQty + r.unusedQty, 0, r.packedQty, r.unusedQty, r.damagedQty,
                    round2(r.packedQty * j.rateSnapshot), 0.0, 0,
                )
            )
        }
        for (p in j.payments) {
            if (!inRange(p.paidAt, from, to)) continue
            rows.add(
                ReportRow("payment-${j.id}-${p.id}", p.paidAt, Activity.PAYMENT, j.itemNameSnapshot, label,
                    j.unitSnapshot, 0, 0, 0, 0, 0, 0.0, round2(p.amount), 0)
            )
        }
    }
    val sorted = rows.sortedWith(compareByDescending<ReportRow> { it.date }.thenBy { it.activity })

    val summary = ReportSummary(
        inventoryOut = sorted.filter { it.activity == Activity.ISSUE }.sumOf { it.outQty },
        inventoryIn = sorted.sumOf { it.inQty },
        homeToOffice = sorted.sumOf { it.packedIn + it.unusedIn },
        restockedIn = sorted.filter { it.activity == Activity.RESTOCK || it.activity == Activity.OPENING }.sumOf { it.inQty },
        packedQty = sorted.sumOf { it.packedIn },
        unusedQty = sorted.sumOf { it.unusedIn },
        damagedQty = sorted.sumOf { it.damagedQty },
        earned = round2(sorted.sumOf { it.earned }),
        paid = round2(sorted.sumOf { it.paid }),
        stockAdjustments = sorted.sumOf { it.stockAdjustment },
        officeStockNow = items.sumOf { it.officeStock },
        atHomeNow = jobs.sumOf { max(0, it.issuedQty - it.reconciled) },
    )
    return Report(items.firstOrNull()?.name ?: "Selected item", from, to, summary, sorted)
}

// ---------- CSV ----------

private fun cell(v: Any?): String = when (v) {
    null -> ""
    is Double -> numStr(v)
    else -> v.toString()
}

private fun csv(rows: List<List<Any?>>): String =
    rows.joinToString("\r\n") { r -> r.joinToString(",") { c -> "\"" + cell(c).replace("\"", "\"\"") + "\"" } }

fun reportCsv(r: Report): String {
    val s = r.summary
    val rows = mutableListOf<List<Any?>>(
        listOf("PackWork item report"),
        listOf("Item", r.itemName),
        listOf("From", r.from),
        listOf("Through", r.to),
        listOf("Office to Home", s.inventoryOut),
        listOf("Home to Office", s.homeToOffice),
        listOf("Stock received", s.restockedIn),
        listOf("Office stock adjustments", s.stockAdjustments),
        listOf("Total inventory in", s.inventoryIn),
        listOf("Packed returned", s.packedQty),
        listOf("Unused returned", s.unusedQty),
        listOf("Damaged", s.damagedQty),
        listOf("Packing earned", s.earned),
        listOf("Payments received", s.paid),
        emptyList(),
        listOf("Date", "Activity", "Item", "Ticket", "Unit", "Office to Home", "Home to Office", "Stock received",
            "Office stock adjustment", "Packed", "Unused", "Damaged at Home", "Earned", "Paid"),
    )
    r.rows.forEach {
        rows.add(listOf(it.date, it.activity, it.itemName, it.ticketLabel, it.unit, it.outQty,
            it.packedIn + it.unusedIn, it.inQty - it.packedIn - it.unusedIn, it.stockAdjustment,
            it.packedIn, it.unusedIn, it.damagedQty, it.earned, it.paid))
    }
    return csv(rows)
}

fun ticketsCsv(store: StoreData): String {
    val rows = mutableListOf<List<Any?>>(
        listOf("Ticket", "Item", "Issued date", "Issued units", "Packed", "Unused", "Damaged", "Open",
            "Rate", "Earned", "Paid", "Owed", "Notes")
    )
    store.jobs.forEach {
        rows.add(listOf(it.id, it.itemNameSnapshot, it.issuedAt, it.issuedQty, it.packed, it.unusedTotal,
            it.damagedTotal, it.open, it.rateSnapshot, it.earned, it.paid, it.owed, it.notes))
    }
    return csv(rows)
}
