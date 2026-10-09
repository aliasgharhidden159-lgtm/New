package com.packwork.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.packwork.tracker.data.open

enum class Screen(val label: String, val title: String, val icon: ImageVector?) {
    Overview("Home", "Work overview", Icons.Outlined.Home),
    Stock("Stock", "Item cupboard", Icons.Outlined.Inventory2),
    Jobs("Jobs", "Work tickets", Icons.Outlined.ReceiptLong),
    Pay("Pay", "Your earnings", Icons.Outlined.Payments),
    Reports("Reports", "Work reports", Icons.Outlined.BarChart),
    Settings("Settings", "Preferences", null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackWorkApp(vm: PackWorkViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Overview) }
    var modal by remember { mutableStateOf<Modal?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.notices.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }
    BackHandler(enabled = screen != Screen.Overview) { screen = Screen.Overview }

    val openTickets = vm.store.jobs.count { it.open > 0 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Eyebrow("PERSONAL WORK LOG")
                        Text(screen.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { screen = Screen.Settings }) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.filter { it.icon != null }.forEach { sc ->
                    NavigationBarItem(
                        selected = screen == sc,
                        onClick = { screen = sc },
                        icon = {
                            if (sc == Screen.Jobs && openTickets > 0) {
                                BadgedBox(badge = { Badge { Text("$openTickets") } }) { Icon(sc.icon!!, sc.label) }
                            } else Icon(sc.icon!!, sc.label)
                        },
                        label = { Text(sc.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(Modifier.padding(inner).consumeWindowInsets(inner).imePadding()) {
            when (screen) {
                Screen.Overview -> OverviewScreen(vm, { modal = it }, { screen = it })
                Screen.Stock -> StockScreen(vm) { modal = it }
                Screen.Jobs -> JobsScreen(vm) { modal = it }
                Screen.Pay -> EarningsScreen(vm) { modal = it }
                Screen.Reports -> ReportsScreen(vm) { modal = it }
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }

    modal?.let { ModalHost(it, vm) { m -> modal = m } }

    vm.confirm?.let { c ->
        AlertDialog(
            onDismissRequest = vm::dismissConfirm,
            title = { Text(c.title) },
            text = { Text(c.message) },
            confirmButton = {
                TextButton(onClick = vm::acceptConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(c.confirmLabel) }
            },
            dismissButton = { TextButton(onClick = vm::dismissConfirm) { Text("Cancel") } },
        )
    }
}
