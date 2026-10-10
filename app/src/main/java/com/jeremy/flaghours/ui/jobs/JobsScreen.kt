package com.jeremy.flaghours.ui.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.data.db.JobWithRo
import com.jeremy.flaghours.domain.JobStatus
import com.jeremy.flaghours.domain.PayCalculator
import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.ui.common.ChoiceChips
import com.jeremy.flaghours.ui.common.EmptyState
import com.jeremy.flaghours.ui.common.PeriodBar
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.fmtDate
import com.jeremy.flaghours.ui.common.hrs
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class JobFilter(val label: String) { OPEN("Open"), UNPAID("Unpaid"), PAID("Paid"), ALL("All") }
enum class PeriodScope(val label: String) { ANY("Any time"), WORKED("Worked in period"), PAID_IN("Paid in period") }

@Composable
fun JobsScreen(onOpenRo: (Long) -> Unit, onNewRo: () -> Unit, onScan: () -> Unit, onSettings: () -> Unit) {
    val repo = app().repository
    val scope = rememberCoroutineScope()
    val jobs by remember { repo.jobs }.collectAsState(initial = emptyList())
    val plan by remember { repo.plan }.collectAsState(initial = PayPlan())
    var filter by rememberSaveable { mutableStateOf(JobFilter.UNPAID) }
    var periodScope by rememberSaveable { mutableStateOf(PeriodScope.ANY) }
    var periodDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val period = PayPeriod.forDate(LocalDate.ofEpochDay(periodDay), plan)
    val snackbar = remember { SnackbarHostState() }
    val byId = jobs.associate { it.job.id to it.toLine() }

    val shown = jobs.filter { j ->
        val statusOk = when (filter) {
            JobFilter.OPEN -> j.job.status == JobStatus.OPEN
            JobFilter.UNPAID -> j.job.status != JobStatus.PAID
            JobFilter.PAID -> j.job.status == JobStatus.PAID
            JobFilter.ALL -> true
        }
        val periodOk = when (periodScope) {
            PeriodScope.ANY -> true
            PeriodScope.WORKED -> j.job.workDate in period
            PeriodScope.PAID_IN -> j.job.paidPeriodStart == period.start
        }
        statusOk && periodOk
    }

    ScreenScaffold(
        title = "Jobs",
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
        snackbar = snackbar,
        fab = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onScan) { Icon(Icons.Default.CameraAlt, "Scan RO") }
                ExtendedFloatingActionButton(onClick = onNewRo, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New RO") })
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChoiceChips(JobFilter.entries, filter, { it.label }, { filter = it })
                ChoiceChips(PeriodScope.entries, periodScope, { it.label }, { periodScope = it })
                if (periodScope != PeriodScope.ANY) {
                    PeriodBar(
                        period,
                        onPrev = { periodDay = PayPeriod.previous(period, plan).start.toEpochDay() },
                        onNext = { periodDay = PayPeriod.next(period, plan).start.toEpochDay() },
                        onToday = { periodDay = LocalDate.now().toEpochDay() },
                    )
                }
                val total = shown.sumOf { PayCalculator.myShare(it.toLine(), plan, byId) }
                Text("${shown.size} lines · ${hrs(total, plan.decimals)} h (my share)", style = MaterialTheme.typography.bodyMedium)
            }
            if (shown.isEmpty()) {
                EmptyState(if (jobs.isEmpty()) "No jobs yet. Tap New RO or scan an RO to add one." else "Nothing matches these filters.")
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.job.id }) { j ->
                    SwipeToPay(
                        enabled = j.job.status != JobStatus.PAID,
                        onPaid = {
                            scope.launch {
                                val before = j.job
                                repo.setJobStatus(before.id, JobStatus.PAID)
                                val r = snackbar.showSnackbar("Marked RO ${j.roNumber} ${before.description} paid", "Undo", duration = SnackbarDuration.Short)
                                if (r == SnackbarResult.ActionPerformed) repo.restoreJob(before)
                            }
                        },
                    ) {
                        JobRow(j, PayCalculator.myShare(j.toLine(), plan, byId), plan) { onOpenRo(j.job.roId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToPay(enabled: Boolean, onPaid: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) onPaid()
            scope.launch { state.snapTo(SwipeToDismissBoxValue.Settled) }
        },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.medium).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("  Mark paid", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { content() }
}

@Composable
private fun JobRow(j: JobWithRo, share: Int, plan: PayPlan, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RO ${j.roNumber} · ${j.job.description}", style = MaterialTheme.typography.titleMedium)
                val vehicle = listOfNotNull(j.year?.toString(), j.make, j.model).joinToString(" ")
                Text(
                    listOfNotNull(fmtDate(j.job.workDate), j.job.laborType.label, vehicle.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${hrs(share, plan.decimals)} h", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AssistChip(onClick = onClick, label = { Text(j.job.status.label) })
            }
        }
    }
}
