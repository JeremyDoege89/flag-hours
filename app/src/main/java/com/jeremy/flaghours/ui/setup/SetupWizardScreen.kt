package com.jeremy.flaghours.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.app
import kotlinx.coroutines.launch

@Composable
fun SetupWizardScreen(onDone: () -> Unit) {
    val repo = app().repository
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<PayPlan?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { plan = repo.currentPlan() }
    val p = plan ?: return
    val steps = PlanStep.entries
    val step = steps[index]
    val last = index == steps.lastIndex

    fun finish(final: PayPlan) {
        scope.launch {
            repo.savePlan(final.copy(setupComplete = true))
            onDone()
        }
    }

    ScreenScaffold(title = "Set up pay plan") { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
            Text("Step ${index + 1} of ${steps.size}: ${step.title}", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                key(step) { PlanStepContent(step, p) { plan = it } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (index > 0) OutlinedButton(onClick = { index-- }, modifier = Modifier.weight(1f).padding(vertical = 4.dp)) { Text("Back") }
                TextButton(
                    onClick = {
                        val reset = step.reset(p)
                        plan = reset
                        if (last) finish(reset) else index++
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Skip") }
                BigButton(if (last) "Finish" else "Next", onClick = { if (last) finish(p) else index++ }, modifier = Modifier.weight(1.4f))
            }
            if (index == 0) {
                TextButton(onClick = { finish(p) }, modifier = Modifier.fillMaxWidth()) { Text("Use defaults for the rest and finish") }
            }
        }
    }
}
