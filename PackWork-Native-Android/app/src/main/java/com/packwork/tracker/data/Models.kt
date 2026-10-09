package com.packwork.tracker.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Item(
    val id: String,
    val name: String,
    val sku: String = "",
    val unit: String = "piece",
    val officeStock: Int = 0,
    val ratePerPackedUnit: Double = 0.0,
    val active: Boolean = true,
)

data class ReturnEntry(
    val id: String,
    val returnedAt: String,
    val packedQty: Int,
    val unusedQty: Int,
    val damagedQty: Int,
    val notes: String = "",
)

data class Payment(
    val id: String,
    val paidAt: String,
    val amount: Double,
    val method: String? = null,
    val reference: String? = null,
)

data class Job(
    val id: String,
    val itemId: String,
    val itemNameSnapshot: String,
    val unitSnapshot: String,
    val issuedQty: Int,
    val rateSnapshot: Double,
    val issuedAt: String,
    val dueAt: String? = null,
    val notes: String = "",
    val returns: List<ReturnEntry> = emptyList(),
    val payments: List<Payment> = emptyList(),
)

object Kind {
    const val OPENING = "opening"
    const val RESTOCK = "restock"
    const val ADJUSTMENT = "adjustment"
    const val DISPATCH = "dispatch"
}

data class StockReceipt(
    val id: String,
    val itemId: String,
    val itemNameSnapshot: String,
    val unitSnapshot: String,
    val quantity: Int,
    val receivedAt: String,
    val kind: String,
    val adjustmentDirection: String? = null, // "in" | "out" (adjustments only)
    val notes: String = "",
)

data class StoreData(
    val items: List<Item> = emptyList(),
    val jobs: List<Job> = emptyList(),
    val stockReceipts: List<StockReceipt> = emptyList(),
    val currency: String = "PKR",
) {
    /** Same JSON shape as the PackWork web app's backup file, so old backups restore here. */
    fun toJson(withMeta: Boolean = false, pretty: Boolean = false): String {
        val root = JSONObject()
        root.put("items", JSONArray().also { a -> items.forEach { a.put(it.toJson()) } })
        root.put("jobs", JSONArray().also { a -> jobs.forEach { a.put(it.toJson()) } })
        root.put("stockReceipts", JSONArray().also { a -> stockReceipts.forEach { a.put(it.toJson()) } })
        root.put("currency", currency)
        if (withMeta) {
            root.put("backupVersion", 1)
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            root.put("exportedAt", iso.format(Date()))
        }
        return if (pretty) root.toString(2) else root.toString()
    }

    companion object {
        /** Returns null if the text is not a valid PackWork store/backup. */
        fun parseOrNull(text: String): StoreData? {
            return try {
                val o = JSONObject(text)
                val items = o.optJSONArray("items") ?: return null
                val jobs = o.optJSONArray("jobs") ?: return null
                StoreData(
                    items = items.mapObjects { itemFrom(it) },
                    jobs = jobs.mapObjects { jobFrom(it) },
                    stockReceipts = o.optJSONArray("stockReceipts").mapObjects { receiptFrom(it) },
                    currency = o.str("currency") ?: "PKR",
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

// ---------- JSON helpers ----------

private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)
private fun JSONObject.int(k: String): Int = optDouble(k, 0.0).let { if (it.isNaN()) 0 else it.toInt() }

private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        out.add(f(o))
    }
    return out
}

private fun Item.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("sku", sku); put("unit", unit)
    put("officeStock", officeStock); put("ratePerPackedUnit", ratePerPackedUnit); put("active", active)
}

private fun itemFrom(o: JSONObject) = Item(
    id = o.str("id") ?: newId(),
    name = o.str("name") ?: "",
    sku = o.str("sku") ?: "",
    unit = o.str("unit") ?: "unit",
    officeStock = o.int("officeStock"),
    ratePerPackedUnit = o.optDouble("ratePerPackedUnit", 0.0).let { if (it.isNaN()) 0.0 else it },
    active = o.optBoolean("active", true),
)

private fun Job.toJson() = JSONObject().apply {
    put("id", id); put("itemId", itemId); put("itemNameSnapshot", itemNameSnapshot)
    put("unitSnapshot", unitSnapshot); put("issuedQty", issuedQty); put("rateSnapshot", rateSnapshot)
    put("issuedAt", issuedAt)
    if (dueAt != null) put("dueAt", dueAt)
    put("notes", notes)
    put("returns", JSONArray().also { a ->
        returns.forEach { r ->
            a.put(JSONObject().apply {
                put("id", r.id); put("returnedAt", r.returnedAt); put("packedQty", r.packedQty)
                put("unusedQty", r.unusedQty); put("damagedQty", r.damagedQty); put("notes", r.notes)
            })
        }
    })
    put("payments", JSONArray().also { a ->
        payments.forEach { p ->
            a.put(JSONObject().apply {
                put("id", p.id); put("paidAt", p.paidAt); put("amount", p.amount)
                if (p.method != null) put("method", p.method)
                if (p.reference != null) put("reference", p.reference)
            })
        }
    })
}

private fun jobFrom(o: JSONObject) = Job(
    id = o.str("id") ?: newId(),
    itemId = o.str("itemId") ?: "",
    itemNameSnapshot = o.str("itemNameSnapshot") ?: "",
    unitSnapshot = o.str("unitSnapshot") ?: "unit",
    issuedQty = o.int("issuedQty"),
    rateSnapshot = o.optDouble("rateSnapshot", 0.0).let { if (it.isNaN()) 0.0 else it },
    issuedAt = o.str("issuedAt") ?: today(),
    dueAt = o.str("dueAt")?.takeIf { it.isNotBlank() },
    notes = o.str("notes") ?: "",
    returns = o.optJSONArray("returns").mapObjects {
        ReturnEntry(
            id = it.str("id") ?: newId(),
            returnedAt = it.str("returnedAt") ?: today(),
            packedQty = it.int("packedQty"),
            unusedQty = it.int("unusedQty"),
            damagedQty = it.int("damagedQty"),
            notes = it.str("notes") ?: "",
        )
    },
    payments = o.optJSONArray("payments").mapObjects {
        Payment(
            id = it.str("id") ?: newId(),
            paidAt = it.str("paidAt") ?: today(),
            amount = it.optDouble("amount", 0.0).let { a -> if (a.isNaN()) 0.0 else a },
            method = it.str("method")?.takeIf { m -> m.isNotBlank() },
            reference = it.str("reference")?.takeIf { r -> r.isNotBlank() },
        )
    },
)

private fun StockReceipt.toJson() = JSONObject().apply {
    put("id", id); put("itemId", itemId); put("itemNameSnapshot", itemNameSnapshot)
    put("unitSnapshot", unitSnapshot); put("quantity", quantity); put("receivedAt", receivedAt)
    put("kind", kind)
    if (adjustmentDirection != null) put("adjustmentDirection", adjustmentDirection)
    put("notes", notes)
}

private fun receiptFrom(o: JSONObject) = StockReceipt(
    id = o.str("id") ?: newId(),
    itemId = o.str("itemId") ?: "",
    itemNameSnapshot = o.str("itemNameSnapshot") ?: "",
    unitSnapshot = o.str("unitSnapshot") ?: "unit",
    quantity = o.int("quantity"),
    receivedAt = o.str("receivedAt") ?: today(),
    kind = o.str("kind") ?: Kind.RESTOCK,
    adjustmentDirection = o.str("adjustmentDirection"),
    notes = o.str("notes") ?: "",
)
