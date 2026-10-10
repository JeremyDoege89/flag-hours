package com.jeremy.flaghours.ui.dashboard

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jeremy.flaghours.ui.common.ScreenScaffold

@Composable
fun DashboardScreen(onSettings: () -> Unit, onNewRo: () -> Unit, onScan: () -> Unit, onPaycheck: () -> Unit) {
    ScreenScaffold(title = "DashboardScreen") { Text("Coming soon") }
}
