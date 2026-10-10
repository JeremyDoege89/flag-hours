package com.jeremy.flaghours.ui.scan

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jeremy.flaghours.ui.common.ScreenScaffold

@Composable
fun ScanScreen(onBack: () -> Unit, onDraftReady: () -> Unit, onManual: () -> Unit) {
    ScreenScaffold(title = "ScanScreen") { Text("Coming soon") }
}
