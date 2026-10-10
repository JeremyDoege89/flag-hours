package com.jeremy.flaghours.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.domain.Guarantee
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.SectionCard
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.hrs
import com.jeremy.flaghours.ui.common.money
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onEditPlan: () -> Unit, onRerunSetup: () -> Unit) {
    val application = app()
    val repo = application.repository
    val plan by remember { repo.plan }.collectAsState(initial = PayPlan())
    val savedKey by application.secrets.olpApiKey.collectAsState()
    var keyText by remember { mutableStateOf(savedKey ?: "") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ScreenScaffold(title = "Settings", onBack = onBack, snackbar = snackbar) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Pay plan") {
                Text("Flag ${money(plan.flagRateCents)}/h · Hourly ${money(plan.hourlyRateCents)}/h")
                Text("${plan.period.label} · ${plan.flagsPayWhen.label}")
                val g = plan.guarantee
                Text(
                    when (g) {
                        Guarantee.None -> "No guarantee"
                        is Guarantee.Flat -> "Guarantee ${hrs(g.hoursHundredths, plan.decimals)} h"
                        is Guarantee.IfClocked -> "Guarantee ${hrs(g.hoursHundredths, plan.decimals)} h if clocked ${hrs(g.minClockedHundredths, plan.decimals)} h"
                    },
                )
                Text(plan.comebackRule.label)
                BigButton("Edit pay plan", onClick = onEditPlan, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onRerunSetup, modifier = Modifier.fillMaxWidth()) { Text("Run setup wizard again") }
            }

            DataSection(snackbar)

            SectionCard("Labor time reference (optional)") {
                Text(
                    "Paste your own Open Labor Project API key to see typical labor-time ranges next to your flags. " +
                        "The free tier allows 10 lookups a day; results are cached. The app works fully without a key.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    keyText, { keyText = it }, label = { Text("API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    application.secrets.setOlpApiKey(keyText)
                    scope.launch { snackbar.showSnackbar(if (keyText.isBlank()) "API key removed" else "API key saved (encrypted)") }
                }) { Text("Save key") }
                Text("Labor reference data: Open Labor Project", style = MaterialTheme.typography.bodySmall)
            }

            SectionCard("About") {
                Text("Flag Hours records what's flagged on your ROs and checks your paycheck. The RO is the source of truth.", style = MaterialTheme.typography.bodyMedium)
                Text("Vehicle decoding: NHTSA vPIC. Labor reference data: Open Labor Project.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
