package com.jeremy.flaghours.ui.paycheck

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jeremy.flaghours.ui.common.ScreenScaffold

@Composable
fun PaycheckScreen(onSettings: () -> Unit, onOpenRo: (Long) -> Unit) {
    ScreenScaffold(title = "PaycheckScreen") { Text("Coming soon") }
}
