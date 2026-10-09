package com.packwork.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.packwork.tracker.data.*
import kotlin.math.max

// ============================== OVERVIEW ==============================

@Composable
fun OverviewScreen(vm: PackWorkViewModel, onModal: (Modal) -> Unit, onNavigate: (Screen) -> Unit) {
    val s = vm.store
    val cur = s.currency
    val activeJobs = s.jobs.filter { it.open > 0 }
    val earned = round2(s.jobs.sumOf { it.earned })
    val paid = round2(s.jobs.sumOf { it.paid })
    val owed = max(0.0, earned - paid)
    val recent = s.jobs.sortedByDescending { it.issuedAt }.take(4)
    val low = s.items.filter { it.active && it.officeStock in 1..9 }

    ScreenList {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader("A CLEAR LOOK AT YOUR WORK", "Good work, accounted for.", "Track Office → Home transfers, Home Godown stock, and what you’ve earned.")
                Button(onClick = { onModal(Modal.Issue) }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Transfer to Home")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Home Godown", "${fmtInt(activeJobs.sumOf { it.open })} units",
                        "${activeJobs.size} open ${if (activeJobs.size == 1) "ticket" else "tickets"}",
                        Icons.Outlined.LocalShipping, Modifier.weight(1f))
                    MetricCard("Earned so far", fmtMoney(earned, cur), "Packed units × saved rates",
                        Icons.Outlined.Payments, Modifier.weight(1f), Tint.Amber)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Still owed", fmtMoney(owed, cur), "${fmtMoney(paid, cur)} paid to date",
                        Icons.Outlined.ReceiptLong, Modifier.weight(1f), Tint.Rose)
                    MetricCard("Office Godown", "${fmtInt(s.items.sumOf { it.officeStock })} units",
                        "${s.items.count { it.active }} active items",
                        Icons.Outlined.Inventory2, Modifier.weight(1f), Tint.Slate)
                }
            }
        }
        item {
            SurfaceCard {
                SectionHeading("IN MOTION", "Open work") {
                    TextButton(onClick = { onNavigate(Screen.Jobs) }) { Text("All tickets") }
                }
                if (activeJobs.isEmpty()) {
                    EmptyState(Icons.Outlined.LocalShipping, "Home Godown is clear",
                        "When you move company goods from Office to Home, the ticket and Home balance will show here.",
                        "Transfer to Home") { onModal(Modal.Issue) }
                } else {
                    activeJobs.take(4).forEach { j ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ItemAvatar(j.itemNameSnapshot)
                            Column(Modifier.weight(1f)) {
                                Text(j.itemNameSnapshot, fontWeight = FontWeight.SemiBold)
                                Text("Office → Home · ${fmtDate(j.issuedAt)} · ${j.issuedQty} ${j.unitSnapshot}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${j.open} ${j.unitSnapshot}", fontWeight = FontWeight.Bold)
                                StatusChip("In progress", false)
                            }
                        }
                    }
                }
            }
        }
        item {
            SurfaceCard {
                SectionHeading("YOUR PAY", "Piece-rate balance")
                Text("Every packed unit is counted at the rate saved when it moved from Office to Home.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KeyValue("Earned", fmtMoney(earned, cur))
                KeyValue("Payments received", fmtMoney(paid, cur))
                HorizontalDivider()
                KeyValue("Still owed", fmtMoney(owed, cur), bold = true)
                TextButton(onClick = { onNavigate(Screen.Pay) }) { Text("See earnings detail") }
            }
        }
        item {
            SurfaceCard {
                SectionHeading("THE PAPER TRAIL", "Recent tickets") {
                    TextButton(onClick = { onNavigate(Screen.Jobs) }) { Text("View history") }
                }
                if (recent.isEmpty()) {
                    EmptyState(Icons.Outlined.ReceiptLong, "Your record starts here",
                        "Office → Home transfers, Home → Office transfers, and payments will build a reliable history as you work.")
                } else {
                    recent.forEach { j ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(j.itemNameSnapshot, fontWeight = FontWeight.SemiBold)
                                Text("Ticket ${j.ticketLabel} · ${j.issuedQty} ${j.unitSnapshot} out · ${j.packed} packed",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmtMoney(j.earned, cur), fontWeight = FontWeight.SemiBold)
                                StatusChip(if (j.open == 0) "Completed" else "In progress", j.open == 0)
                            }
                        }
                    }
                }
            }
        }
        if (low.isNotEmpty()) {
            item {
                InfoNote(Icons.Outlined.Warning, "Running low: " + low.joinToString(", ") { "${it.name} (${it.officeStock})" } + ". Review them on the Stock tab.", warn = true)
            }
        }
    }
}

// ============================== STOCK ==============================

@Composable
fun StockScreen(vm: PackWorkViewModel, onModal: (Modal) -> Unit) {
    val s = vm.store
    var query by rememberSaveable { mutableStateOf("") }
    val shown = s.items.filter { "${it.name} ${it.sku}".lowercase().contains(query.lowercase()) }
    val homeTotal = s.jobs.sumOf { it.open }

    ScreenList {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader("OFFICE & HOME GODOWNS", "Stock by godown", "Track each reusable item in both stock locations.")
                Button(onClick = { onModal(Modal.ItemForm()) }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add item")
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search item or SKU") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${fmtInt(s.items.sumOf { it.officeStock })} Office · ${fmtInt(homeTotal)} Home",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (shown.isEmpty()) {
            item {
                SurfaceCard {
                    EmptyState(Icons.Outlined.Inventory2,
                        if (query.isNotEmpty()) "No matching items" else "No items added yet",
                        if (query.isNotEmpty()) "Try another item name or SKU." else "Add the goods you work on so issues and transfers stay connected to real stock.",
                        if (query.isEmpty()) "Add your first item" else null,
                        if (query.isEmpty()) ({ onModal(Modal.ItemForm()) }) else null)
                }
            }
        } else {
            items(shown, key = { it.id }) { item -> ItemCard(item, vm, onModal) }
        }
        item {
            InfoNote(Icons.Outlined.Info, "Office → Home transfers reduce Office stock and increase Home Godown stock. Only packed and unused units transfer back to Office; damaged units are reconciled out of Home.")
        }
    }
}

@Composable
private fun ItemCard(item: Item, vm: PackWorkViewModel, onModal: (Modal) -> Unit) {
    val s = vm.store
    val home = s.jobs.filter { it.itemId == item.id }.sumOf { it.open }
    val entries = s.stockReceipts.filter { it.itemId == item.id }.sortedByDescending { it.receivedAt }
    var showEntries by remember(item.id) { mutableStateOf(false) }

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemAvatar(item.name, 46)
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${item.sku.ifBlank { "No SKU" }} · ${item.unit} based",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(if (item.active) "Active" else "Paused", item.active)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StockBox("Office Godown", "${fmtInt(item.officeStock)} ${item.unit}", Modifier.weight(1f))
            StockBox("Home Godown", "${fmtInt(home)} ${item.unit}", Modifier.weight(1f))
        }
        KeyValue("Earn rate", "${fmtMoney(item.ratePerPackedUnit, s.currency)} per packed unit")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onModal(Modal.ItemForm(item.id)) }) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit")
            }
            TextButton(onClick = { onModal(Modal.Restock(item.id)) }) {
                Icon(Icons.Outlined.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Receive")
            }
            TextButton(onClick = { onModal(Modal.Dispatch(item.id)) }, enabled = item.officeStock > 0) {
                Icon(Icons.Outlined.Upload, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Dispatch")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showEntries = !showEntries }) {
                Text("Stock entries · ${entries.size}")
                Icon(if (showEntries) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.requestDeleteItem(item.id) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Delete")
            }
        }
        if (showEntries) {
            if (entries.isEmpty()) {
                Text("No stock entries yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entries.forEach { e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (e.kind) {
                                Kind.OPENING -> "Opening stock"
                                Kind.RESTOCK -> "Stock received"
                                Kind.DISPATCH -> "Dispatched out of Office"
                                else -> "Office stock adjustment ${if (e.adjustmentDirection == "out") "(decrease)" else "(increase)"}"
                            }, fontWeight = FontWeight.SemiBold,
                        )
                        Text(fmtDate(e.receivedAt) + if (e.notes.isNotBlank()) " · ${e.notes}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text((if (e.adjustmentDirection == "out") "−" else "+") + "${e.quantity} ${e.unitSnapshot}", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.requestDeleteReceipt(e.id) }) {
                        Icon(Icons.Outlined.Delete, "Delete entry", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StockBox(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
