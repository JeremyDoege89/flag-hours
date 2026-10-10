package com.jeremy.flaghours.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.data.db.Shift
import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.Units
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.PeriodBar
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.StatCard
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.hrs
import com.jeremy.flaghours.ui.common.minutesLabel
import com.jeremy.flaghours.ui.common.money
import com.jeremy.flaghours.ui.common.rememberLiveData
import com.jeremy.flaghours.ui.time.ClockCard
import com.jeremy.flaghours.ui.time.FixShiftDialog
import java.time.LocalDate

@Composable
fun DashboardScreen(onSettings: () -> Unit, onNewRo: () -> Unit, onScan: () -> Unit, onPaycheck: () -> Unit) {
    val repo = app().repository
    val live = rememberLiveData(repo)
    val plan = live.plan
    val open by remember { repo.openShift }.collectAsState(initial = null)
    var periodDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showMath by rememberSaveable { mutableStateOf(false) }
    var fixing by remember { mutableStateOf<Shift?>(null) }
    val period = PayPeriod.forDate(LocalDate.ofEpochDay(periodDay), plan)
    val s = live.summary(period)
    val d = plan.decimals

    ScreenScaffold(title = "Flag Hours", actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (live.loaded && plan.flagRateCents == 0L) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Your flag rate is $0. Set it to see pay estimates.")
                        TextButton(onClick = onSettings) { Text("Open settings") }
                    }
                }
            }
            ClockCard(open, live.now, repo, onFix = { fixing = it })
            PeriodBar(
                period,
                onPrev = { periodDay = PayPeriod.previous(period, plan).start.toEpochDay() },
                onNext = { periodDay = PayPeriod.next(period, plan).start.toEpochDay() },
                onToday = { periodDay = LocalDate.now().toEpochDay() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Worked", hrs(s.flaggedWorked, d), Modifier.weight(1f), sub = "flag hours done")
                StatCard("Paid", hrs(s.flaggedPaid, d), Modifier.weight(1f), sub = "on this check", highlight = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Still owed", hrs(s.stillOwed, d), Modifier.weight(1f), sub = "not paid yet")
                StatCard("Clocked", hrs(s.clockedHundredths, d), Modifier.weight(1f), sub = minutesLabel(s.clockedMinutes))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Efficiency", s.efficiencyPermille?.let { Units.formatPermille(it) } ?: "—", Modifier.weight(1f), sub = "worked ÷ clocked")
                StatCard("Est. gross", money(s.grossCents), Modifier.weight(1f), sub = "this check", highlight = true)
            }
            if (s.hourlyMinutes > 0 || s.guaranteeTopUpHundredths > 0) {
                Text(
                    buildList {
                        if (s.hourlyMinutes > 0) add("Hourly ${hrs(s.hourlyHundredths, d)} h")
                        if (s.guaranteeTopUpHundredths > 0) add("Guarantee top-up ${hrs(s.guaranteeTopUpHundredths, d)} h")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Card(onClick = { showMath = !showMath }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text("How the estimate is calculated", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(if (showMath) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    if (showMath) s.math.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("New RO", onClick = onNewRo, icon = Icons.Default.Add, modifier = Modifier.weight(1f))
                BigButton("Scan RO", onClick = onScan, icon = Icons.Default.CameraAlt, modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = onPaycheck, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Check a paycheck") }
        }
    }

    fixing?.let { shift ->
        FixShiftDialog(shift, live.now, onDone = { fixing = null })
    }
}
