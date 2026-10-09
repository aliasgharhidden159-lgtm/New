package com.packwork.tracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.packwork.tracker.data.*

// ============================== REPORTS ==============================

@Composable
fun ReportsScreen(vm: PackWorkViewModel, onModal: (Modal) -> Unit) {
    val s = vm.store
    var itemId by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf(monthStart()) }
    var to by rememberSaveable { mutableStateOf(today()) }
    var report by remember { mutableStateOf<Report?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s) { report = null }  // data changed -> stale report

    val selected = s.items.find { it.id == itemId }
    val saveCsv = rememberFileSaver("text/csv") { ok -> vm.notify(if (ok) "Item report saved." else "Could not save the file.") }

    ScreenList {
        item { PageHeader("THE PAPER TRAIL", "Work reports", "Track transfers between the Office and Home Godowns, along with packing earnings and payments.") }
        item {
            SurfaceCard {
                SectionHeading("SET YOUR WINDOW", "Build a report")
                if (s.items.isEmpty()) {
                    EmptyState(Icons.Outlined.Inventory2, "Your item list is empty",
                        "Add a reusable work item before generating a report.", "Add an item") { onModal(Modal.ItemForm()) }
                } else {
                    DropdownField("Work item", selected?.let { "${it.name} · ${it.unit}" } ?: "Choose an item",
                        s.items, { "${it.name} · ${it.unit}" }, { itemId = it.id; error = null })
                    DateField("From", from, { from = it })
                    DateField("Through", to, { to = it })
                    val badRange = from.isNotBlank() && to.isNotBlank() && from > to
                    if (badRange) Text("The start date must be on or before the end date.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        enabled = !badRange,
                        onClick = {
                            error = when {
                                selected == null -> "Choose an item before generating the report."
                                from.isBlank() || to.isBlank() -> "Choose both a start date and an end date."
                                else -> null
                            }
                            report = if (error == null) buildReport(s, itemId, from, to) else null
                        },
                    ) { Text("Generate report") }
                }
            }
        }
        val r = report
        if (r == null) {
            if (s.items.isNotEmpty()) item {
                SurfaceCard {
                    EmptyState(Icons.Outlined.BarChart, "Choose an item and date window to begin",
                        "Your report will show Office → Home and Home → Office movement, packing earnings, and payments for one item.")
                }
            }
        } else {
            val sm = r.summary
            val cur = s.currency
            item {
                SurfaceCard {
                    Eyebrow("REPORT WINDOW")
                    Text(r.itemName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${fmtDate(r.from)} — ${fmtDate(r.to)} · ${r.rows.size} ${if (r.rows.size == 1) "activity row" else "activity rows"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { saveCsv("packwork-report-${today()}.csv", reportCsv(r)) }) {
                        Icon(Icons.Outlined.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Export CSV")
                    }
                }
            }
            item {
                val tiles = listOf(
                    Triple("Office → Home", fmtInt(sm.inventoryOut), "Transferred from Office"),
                    Triple("Home → Office", fmtInt(sm.homeToOffice), "Packed and unused"),
                    Triple("Stock received", fmtInt(sm.restockedIn), "Opening and receipts"),
                    Triple("Office adjustments", fmtSigned(sm.stockAdjustments), "Corrections and dispatches"),
                    Triple("Total inventory in", fmtInt(sm.inventoryIn), "Home + stock receipts"),
                    Triple("Packed returned", fmtInt(sm.packedQty), "Completed units"),
                    Triple("Unused returned", fmtInt(sm.unusedQty), "Returned, not packed"),
                    Triple("Damaged at Home", fmtInt(sm.damagedQty), "Not sent to Office"),
                    Triple("Packing earned", fmtMoney(sm.earned, cur), "For packed work"),
                    Triple("Payments received", fmtMoney(sm.paid, cur), "Cash recorded as paid"),
                    Triple("Office Godown now", fmtInt(sm.officeStockNow), "Current quantity"),
                    Triple("Home Godown now", fmtInt(sm.atHomeNow), "Current quantity"),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow("WINDOW TOTALS")
                    tiles.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (label, value, note) ->
                                Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { InfoNote(Icons.Outlined.Info, "Packed and unused quantities count as Home → Office transfers. Stock receipts enter Office separately; damaged quantities leave Home stock but never enter Office.") }
            item {
                SectionHeading("ITEM-LEVEL LEDGER", "Activity in this window") {
                    Text("${r.rows.size} ${if (r.rows.size == 1) "entry" else "entries"}", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (r.rows.isEmpty()) {
                item {
                    SurfaceCard { EmptyState(Icons.Outlined.BarChart, "No activity in this window", "There are no matching transfers, stock receipts, adjustments, or payments for this item and date range.") }
                }
            } else {
                items(r.rows, key = { it.id }) { row ->
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.activity, fontWeight = FontWeight.Bold)
                                Text("${fmtDate(row.date)}" + if (row.ticketLabel != "—") " · ${row.ticketLabel}" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val u = row.unit
                        if (row.outQty > 0) KeyValue("Office → Home", "${fmtInt(row.outQty)} $u")
                        if (row.packedIn + row.unusedIn > 0) KeyValue("Home → Office", "${fmtInt(row.packedIn + row.unusedIn)} $u")
                        val received = row.inQty - row.packedIn - row.unusedIn
                        if (received > 0) KeyValue("Stock received", "${fmtInt(received)} $u")
                        if (row.stockAdjustment != 0) KeyValue("Stock adjustment", "${fmtSigned(row.stockAdjustment)} $u")
                        if (row.packedIn > 0) KeyValue("Packed", "${fmtInt(row.packedIn)} $u")
                        if (row.unusedIn > 0) KeyValue("Unused", "${fmtInt(row.unusedIn)} $u")
                        if (row.damagedQty > 0) KeyValue("Damaged", "${fmtInt(row.damagedQty)} $u")
                        if (row.earned > 0) KeyValue("Earned", fmtMoney(row.earned, cur))
                        if (row.paid > 0) KeyValue("Paid", fmtMoney(row.paid, cur))
                    }
                }
            }
            item { InfoNote(Icons.Outlined.Payments, "Packing earned is calculated from packed units. Payments received are cash entries logged against tickets; one does not imply the other.") }
        }
    }
}

// ============================== SETTINGS ==============================

@Composable
fun SettingsScreen(vm: PackWorkViewModel) {
    val s = vm.store
    val context = LocalContext.current
    val currencies = listOf("PKR", "USD", "INR", "AED", "SAR", "CAD", "AUD", "GBP", "EUR", "NZD")

    val saveJson = rememberFileSaver("application/json") { ok -> vm.notify(if (ok) "Backup saved. Keep it somewhere safe." else "Could not save the file.") }
    val saveCsv = rememberFileSaver("text/csv") { ok -> vm.notify(if (ok) "CSV export saved." else "Could not save the file.") }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            if (text == null) vm.notify("Could not read that file.") else vm.requestRestore(text)
        }
    }

    ScreenList {
        item { PageHeader("YOUR RECORDS, YOURS", "Settings & backup", "Choose how amounts are shown and keep a portable copy of your work history.") }
        item {
            SurfaceCard {
                SectionHeading("DISPLAY", "Currency")
                Text("Used for piece rates and payment amounts. Saved with your records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DropdownField("Currency", s.currency, currencies, { it }, { vm.setCurrency(it) })
            }
        }
        item {
            SurfaceCard {
                SectionHeading("BACKUP", "Backup & restore")
                InfoNote(Icons.Outlined.Shield, "Your data is stored only in this app on this device. A backup file is the way to move records to another phone or recover them if app data is cleared. Backups from the old web/WebView version restore here too.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { saveJson("packwork-backup-${today()}.json", vm.backupJson()) }) {
                        Icon(Icons.Outlined.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Save backup")
                    }
                    OutlinedButton(onClick = { restore.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.Upload, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Restore")
                    }
                }
            }
        }
        item {
            SurfaceCard {
                SectionHeading("SPREADSHEET", "Export tickets")
                Text("Save your work tickets and earnings as a CSV file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { saveCsv("packwork-records-${today()}.csv", ticketsCsv(s)) }) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Export tickets as CSV")
                }
            }
        }
        item {
            SurfaceCard {
                Eyebrow("PRIVATE BY DESIGN")
                Text("Just your work. On your device.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("PackWork has no account and no server, and works fully offline. Records are saved automatically on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Automatic local saving", "No sign-in or network access", "JSON and CSV files you control").forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
