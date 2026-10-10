package com.jeremy.flaghours.ui.paycheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.data.db.PayStub
import com.jeremy.flaghours.domain.GapExplanation
import com.jeremy.flaghours.domain.GapReason
import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.Reconciler
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.HoursField
import com.jeremy.flaghours.ui.common.MoneyField
import com.jeremy.flaghours.ui.common.PeriodBar
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.SectionCard
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.hrs
import com.jeremy.flaghours.ui.common.money
import com.jeremy.flaghours.ui.common.rememberLiveData
import com.jeremy.flaghours.ui.theme.BigNumber
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PaycheckScreen(onSettings: () -> Unit, onOpenRo: (Long) -> Unit) {
    val repo = app().repository
    val scope = rememberCoroutineScope()
    val live = rememberLiveData(repo)
    val plan = live.plan
    // A paycheck usually covers the period that just ended.
    var periodDay by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(live.loaded) {
        if (live.loaded && periodDay == null) {
            periodDay = PayPeriod.previous(PayPeriod.forDate(repo.today(), plan), plan).start.toEpochDay()
        }
    }
    val period = PayPeriod.forDate(LocalDate.ofEpochDay(periodDay ?: LocalDate.now().toEpochDay()), plan)
    val stub by remember(period.start) { repo.observeStub(period.start) }.collectAsState(initial = null)
    var stubHours by remember(period.start, stub) { mutableStateOf(stub?.hoursHundredths) }
    var stubGross by remember(period.start, stub) { mutableStateOf(stub?.grossCents) }
    val summary = live.summary(period)
    val d = plan.decimals
    val rec = stub?.let { Reconciler.reconcile(it.hoursHundredths, it.grossCents, summary, live.jobs.map { j -> j.toLine() }, plan) }

    ScreenScaffold(title = "Paycheck check", actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PeriodBar(
                    period,
                    onPrev = { periodDay = PayPeriod.previous(period, plan).start.toEpochDay() },
                    onNext = { periodDay = PayPeriod.next(period, plan).start.toEpochDay() },
                    label = "Pay period on the stub",
                )
            }
            item {
                key(period.start, stub) {
                    SectionCard("From your pay stub") {
                        HoursField(stubHours, { stubHours = it }, "Flag + hourly hours paid")
                        MoneyField(stubGross, { stubGross = it }, "Gross pay (optional)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BigButton(
                                "Check",
                                onClick = { stubHours?.let { h -> scope.launch { repo.saveStub(PayStub(period.start, h, stubGross)) } } },
                                enabled = stubHours != null,
                                modifier = Modifier.weight(1f),
                            )
                            if (stub != null) {
                                OutlinedButton(onClick = { scope.launch { repo.deleteStub(stub!!) } }, modifier = Modifier.weight(1f).padding(top = 4.dp)) { Text("Clear") }
                            }
                        }
                    }
                }
            }
            item {
                SectionCard("What you recorded") {
                    Line("Flag hours paid this period", "${hrs(summary.flaggedPaid, d)} h")
                    if (summary.hourlyMinutes > 0) Line("Hourly time", "${hrs(summary.hourlyHundredths, d)} h")
                    if (summary.guaranteeTopUpHundredths > 0) Line("Guarantee top-up", "${hrs(summary.guaranteeTopUpHundredths, d)} h")
                    HorizontalDivider()
                    Line("Expected on stub", "${hrs(summary.flaggedPaid + summary.hourlyHundredths + summary.guaranteeTopUpHundredths, d)} h", bold = true)
                    Line("Estimated gross", money(summary.grossCents), bold = true)
                }
            }
            if (rec != null) {
                item {
                    val ok = rec.gapHundredths == 0
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (ok) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (ok) {
                                Text("Hours match", style = BigNumber)
                                Text("The stub matches what you recorded.")
                            } else {
                                val g = rec.gapHundredths
                                Text("${if (g > 0) "+" else ""}${hrs(g, d)} h", style = BigNumber)
                                Text(if (g > 0) "The stub paid more hours than you recorded as paid." else "The stub paid fewer hours than you recorded as paid.")
                            }
                            rec.grossGapCents?.let { gg ->
                                if (gg != 0L) Text("Gross differs from the estimate by ${if (gg > 0) "+" else ""}${money(gg)}.")
                            }
                        }
                    }
                }
                if (rec.gapHundredths != 0) {
                    item {
                        Text(
                            if (rec.explanations.isEmpty()) "No recorded jobs explain the gap. Check for lines missing from the app or hours the shop didn't pay."
                            else "Jobs that could explain it",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(rec.explanations, key = { "${it.reason}-${it.job.id}" }) { e ->
                        ExplanationCard(
                            e, d,
                            onOpen = { onOpenRo(e.job.roId) },
                            onMarkPaidHere = { scope.launch { repo.markPaidInPeriod(e.job.id, period.start) } },
                            onMoveNext = { scope.launch { repo.movePaidPeriod(e.job.id, PayPeriod.next(period, plan).start) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

@Composable
private fun ExplanationCard(e: GapExplanation, decimals: Int, onOpen: () -> Unit, onMarkPaidHere: () -> Unit, onMoveNext: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(e.reason.label) })
                if (e.exactMatch) AssistChip(onClick = {}, label = { Text("Exact match") })
                Text(
                    if (e.deltaHundredths != 0) "${if (e.deltaHundredths > 0) "+" else ""}${hrs(e.deltaHundredths, decimals)} h" else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(e.message)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (e.reason) {
                    GapReason.NOT_MARKED_PAID, GapReason.PAID_IN_OTHER_PERIOD ->
                        OutlinedButton(onClick = onMarkPaidHere) { Text("Mark paid this period") }
                    GapReason.MARKED_PAID_HERE ->
                        OutlinedButton(onClick = onMoveNext) { Text("Move to next period") }
                    else -> Unit
                }
                TextButton(onClick = onOpen) { Text("Open RO") }
            }
        }
    }
}
