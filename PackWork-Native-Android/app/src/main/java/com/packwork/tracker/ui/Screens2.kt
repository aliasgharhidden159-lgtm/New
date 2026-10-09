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
import kotlin.math.min

// ============================== TICKETS ==============================

@Composable
fun JobsScreen(vm: PackWorkViewModel, onModal: (Modal) -> Unit) {
    val s = vm.store
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val shown = s.jobs.sortedByDescending { it.issuedAt }.filter {
        "${it.itemNameSnapshot} ${it.notes} ${it.id}".lowercase().contains(query.lowercase()) &&
            (filter == "all" || (filter == "open" && it.open > 0) || (filter == "done" && it.open == 0))
    }

    ScreenList {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader("OFFICE → HOME & HOME → OFFICE", "Work tickets", "Track each Office-to-Home transfer, then reconcile what moves back from Home.")
                Button(onClick = { onModal(Modal.Issue) }, enabled = s.items.any { it.active }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Transfer to Home")
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Find an item or note") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All tickets", "open" to "In progress", "done" to "Completed").forEach { (k, label) ->
                        FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                SurfaceCard {
                    val none = s.jobs.isEmpty()
                    EmptyState(Icons.Outlined.ReceiptLong,
                        if (none) "No transfers yet" else "No tickets match",
                        if (none) "Move an item from Office to Home to create a ticket and add it to the Home Godown balance." else "Try a different search or ticket filter.",
                        if (none && s.items.any { it.active }) "Transfer to Home" else null,
                        if (none && s.items.any { it.active }) ({ onModal(Modal.Issue) }) else null)
                }
            }
        } else {
            items(shown, key = { it.id }) { job -> TicketCard(job, vm, onModal) }
        }
    }
}

@Composable
private fun TicketCard(job: Job, vm: PackWorkViewModel, onModal: (Modal) -> Unit) {
    var expanded by remember(job.id) { mutableStateOf(false) }
    val cur = vm.store.currency
    val open = job.open
    val reconciled = job.reconciled
    val unpaid = job.owed
    val fraction = if (job.issuedQty > 0) min(1f, reconciled.toFloat() / job.issuedQty) else 1f

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemAvatar(job.itemNameSnapshot)
            Column(Modifier.weight(1f)) {
                Text(job.itemNameSnapshot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Ticket ${job.ticketLabel} · Office → Home ${fmtDate(job.issuedAt)}" + (job.dueAt?.let { " · due ${fmtDate(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(if (open == 0) "Completed" else "In progress", open == 0)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Reconciled $reconciled of ${job.issuedQty} ${job.unitSnapshot}", style = MaterialTheme.typography.bodySmall)
            Text("${if (job.issuedQty > 0) Math.round(reconciled * 100.0 / job.issuedQty) else 100}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("In Home", "$open ${job.unitSnapshot}", Modifier.weight(1f))
            Stat("Packed", "${job.packed}", Modifier.weight(1f))
            Stat("Earned", fmtMoney(job.earned, cur), Modifier.weight(1.4f))
            Stat("Unpaid", fmtMoney(unpaid, cur), Modifier.weight(1.4f))
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide details" else "Show details")
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (expanded) {
            KeyValue("Saved piece rate", "${fmtMoney(job.rateSnapshot, cur)} / ${job.unitSnapshot}")
            KeyValue("Returns", "${job.returns.size}")
            KeyValue("Payments", "${job.payments.size}")
            if (job.notes.isNotBlank()) {
                Text(job.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (open > 0) FilledTonalButton(onClick = { onModal(Modal.Return(job.id)) }) { Text("Transfer to Office") }
                if (unpaid > 0) FilledTonalButton(onClick = { onModal(Modal.Payment(job.id)) }) { Text("Record payment") }
            }
            if (open == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fully reconciled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            job.returns.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${fmtDate(r.returnedAt)} Home reconciliation", fontWeight = FontWeight.SemiBold)
                        Text("${r.packedQty} packed · ${r.unusedQty} unused · ${r.damagedQty} damaged" + if (r.notes.isNotBlank()) "\n${r.notes}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.requestDeleteReturn(job.id, r.id) }) {
                        Icon(Icons.Outlined.Delete, "Delete reconciliation", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
            job.payments.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${fmtDate(p.paidAt)} payment" + (p.method?.let { " · $it" } ?: ""), fontWeight = FontWeight.SemiBold)
                        Text(p.reference ?: "Payment record", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(fmtMoney(p.amount, cur), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.requestDeletePayment(job.id, p.id) }) {
                        Icon(Icons.Outlined.Delete, "Delete payment", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
            TextButton(onClick = { vm.requestDeleteJob(job.id) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Delete ticket")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// ============================== EARNINGS ==============================

@Composable
fun EarningsScreen(vm: PackWorkViewModel, onModal: (Modal) -> Unit) {
    val s = vm.store
    val cur = s.currency
    val earned = round2(s.jobs.sumOf { it.earned })
    val paid = round2(s.jobs.sumOf { it.paid })
    val owed = max(0.0, earned - paid)
    val rows = s.jobs.filter { it.earned > 0 }.sortedByDescending { it.issuedAt }

    ScreenList {
        item { PageHeader("PIECE-RATE PAY", "Your earnings", "Your pay is calculated from packed units at the rate saved on each ticket.") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TOTAL STILL OWED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                    Text(fmtMoney(owed, cur), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Text("Based on ${fmtInt(s.jobs.sumOf { it.packed })} packed units across ${rows.size} ${if (rows.size == 1) "ticket" else "tickets"}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Total earned", fmtMoney(earned, cur), "Packed × ticket rate", Icons.Outlined.Payments, Modifier.weight(1f), Tint.Amber)
                MetricCard("Payments received", fmtMoney(paid, cur), "All recorded payments", Icons.Outlined.Download, Modifier.weight(1f), Tint.Slate)
            }
        }
        item {
            SurfaceCard {
                SectionHeading("BY WORK TICKET", "Earned & paid") { Text("${rows.size} tickets", style = MaterialTheme.typography.labelMedium) }
                if (rows.isEmpty()) {
                    EmptyState(Icons.Outlined.Payments, "Earnings will appear here", "When you log packed returns, the piece-rate amount is calculated automatically.")
                }
            }
        }
        items(rows, key = { it.id }) { j ->
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(j.itemNameSnapshot, fontWeight = FontWeight.Bold)
                        Text("Issued ${fmtDate(j.issuedAt)} · Ticket ${j.ticketLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(fmtMoney(j.owed, cur), fontWeight = FontWeight.Bold, color = if (j.owed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
                KeyValue("Packed", "${j.packed} ${j.unitSnapshot} × ${fmtMoney(j.rateSnapshot, cur)}")
                KeyValue("Earned", fmtMoney(j.earned, cur))
                KeyValue("Paid", fmtMoney(j.paid, cur))
                if (j.owed > 0) FilledTonalButton(onClick = { onModal(Modal.Payment(j.id)) }) { Text("Add payment") }
            }
        }
        item { InfoNote(Icons.Outlined.Shield, "Ticket rates are locked at issue time, so editing an item’s current rate never changes past earnings.") }
    }
}
