package com.jeremy.flaghours.ui.ro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.data.db.Job
import com.jeremy.flaghours.data.db.RepairOrder
import com.jeremy.flaghours.domain.JobStatus
import com.jeremy.flaghours.domain.PayCalculator
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.ui.common.ChoiceChips
import com.jeremy.flaghours.ui.common.ConfirmDialog
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.onMain
import com.jeremy.flaghours.ui.common.fmtDate
import com.jeremy.flaghours.ui.common.hrs
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun RoDetailScreen(roId: Long, onBack: () -> Unit, onEditRo: (Long) -> Unit) {
    val repo = app().repository
    val scope = rememberCoroutineScope()
    val ro by remember(roId) { repo.observeRo(roId) }.collectAsState(initial = null)
    val jobs by remember(roId) { repo.observeJobsForRo(roId) }.collectAsState(initial = emptyList())
    val allJobs by remember { repo.jobs }.collectAsState(initial = emptyList())
    val plan by remember { repo.plan }.collectAsState(initial = PayPlan())
    var editing by remember { mutableStateOf<Job?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val r = ro ?: run {
        ScreenScaffold(title = "RO", onBack = onBack) {}
        return
    }
    val byId = allJobs.associate { it.job.id to it.toLine() }

    ScreenScaffold(
        title = "RO ${r.roNumber}",
        onBack = onBack,
        actions = {
            IconButton(onClick = { onEditRo(r.id) }) { Icon(Icons.Default.Edit, "Edit RO") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete RO") }
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { editing = Job(roId = r.id, description = "", flaggedHundredths = 0, workDate = repo.today()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add line") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RoHeader(r, plan) }
            if (jobs.isEmpty()) item { Text("No labor lines yet. Tap Add line.") }
            items(jobs, key = { it.id }) { job ->
                JobCard(
                    job = job,
                    share = PayCalculator.myShare(job.toLine(r.roNumber), plan, byId),
                    plan = plan,
                    onClick = { editing = job },
                    onStatus = { s -> scope.launch { repo.setJobStatus(job.id, s) } },
                )
            }
            item {
                val total = jobs.sumOf { PayCalculator.myShare(it.toLine(r.roNumber), plan, byId) }
                Text("My total on this RO: ${hrs(total, plan.decimals)} h", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    editing?.let { j ->
        JobEditorDialog(
            initial = j,
            ro = r,
            plan = plan,
            allJobs = allJobs,
            onSave = { updated -> scope.launch { repo.saveJob(updated, r); editing = null } },
            onDelete = if (j.id != 0L) ({ scope.launch { repo.deleteJob(j); editing = null } }) else null,
            onDismiss = { editing = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete RO ${r.roNumber}?",
            "This deletes the RO and all ${jobs.size} of its lines. This can't be undone.",
            "Delete",
            onConfirm = { scope.launch { repo.deleteRo(r); onMain(onBack) } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun RoHeader(r: RepairOrder, plan: PayPlan, extra: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("RO ${r.roNumber} · ${fmtDate(r.openedDate)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (r.vehicleLabel.isNotBlank()) Text(r.vehicleLabel + (r.engine?.let { " · $it" } ?: ""))
            r.vin?.let { Text("VIN $it", style = MaterialTheme.typography.bodyMedium) }
            r.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            extra()
        }
    }
}

@Composable
fun JobCard(job: Job, share: Int, plan: PayPlan, onClick: () -> Unit, onStatus: (JobStatus) -> Unit, badge: @Composable () -> Unit = {}) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.description, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${hrs(share, plan.decimals)} h", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val details = buildList {
                add(job.laborType.label)
                add("Flagged ${hrs(job.flaggedHundredths, plan.decimals)}")
                job.adjustedHundredths?.let { add("Adjusted ${hrs(it, plan.decimals)} (${hrs(job.flaggedHundredths - it, plan.decimals)} cut)") }
                if (job.mySplitPct != 100) add("${job.mySplitPct}% split")
                if (job.comebackOfJobId != null) add("Comeback")
                add("Worked ${fmtDate(job.workDate)}")
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            badge()
            ChoiceChips(JobStatus.entries, job.status, { it.label }, onStatus)
        }
    }
}
