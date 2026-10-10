package com.jeremy.flaghours.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.domain.PaySummary
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.SectionCard
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.fmtPeriod
import com.jeremy.flaghours.ui.common.money
import com.jeremy.flaghours.ui.setup.PlanStep
import com.jeremy.flaghours.ui.setup.PlanStepContent
import kotlinx.coroutines.launch

private data class Preview(val old: PaySummary, val new: PaySummary)

@Composable
fun PlanEditScreen(onBack: () -> Unit) {
    val repo = app().repository
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<PayPlan?>(null) }
    var draft by remember { mutableStateOf<PayPlan?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    LaunchedEffect(Unit) {
        val p = repo.currentPlan()
        original = p
        draft = p
    }
    val d = draft ?: return
    val o = original ?: return

    ScreenScaffold(title = "Pay plan", onBack = onBack) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlanStep.entries.forEach { step ->
                SectionCard(step.title) { PlanStepContent(step, d) { draft = it } }
            }
            BigButton(
                "Review changes & save",
                onClick = {
                    scope.launch {
                        val snap = repo.snapshot()
                        val today = repo.today()
                        val oldPeriod = PayPeriod.previous(PayPeriod.forDate(today, o), o)
                        val newPeriod = PayPeriod.previous(PayPeriod.forDate(today, d), d)
                        preview = Preview(repo.summarize(snap, oldPeriod, o), repo.summarize(snap, newPeriod, d))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = d != o,
            )
        }
    }

    preview?.let { pv ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("What changes") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Last period (${fmtPeriod(pv.new.period)}) recalculated with the new plan:")
                    Text("Before: ${money(pv.old.grossCents)}")
                    Text("After: ${money(pv.new.grossCents)}", fontWeight = FontWeight.Bold)
                    val diff = pv.new.grossCents - pv.old.grossCents
                    Text("Difference: ${if (diff >= 0) "+" else ""}${money(diff)}")
                    if (pv.old.period != pv.new.period) Text("Period changes from ${fmtPeriod(pv.old.period)} to ${fmtPeriod(pv.new.period)}.")
                    Text("New math", style = MaterialTheme.typography.titleSmall)
                    pv.new.math.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.savePlan(d.copy(setupComplete = true))
                        preview = null
                        onBack()
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Keep editing") } },
        )
    }
}
