package com.jeremy.flaghours.ui.ro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jeremy.flaghours.data.db.Job
import com.jeremy.flaghours.data.db.JobWithRo
import com.jeremy.flaghours.data.db.RepairOrder
import com.jeremy.flaghours.domain.JobStatus
import com.jeremy.flaghours.domain.LaborType
import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.ChoiceChips
import com.jeremy.flaghours.ui.common.ClickableRow
import com.jeremy.flaghours.ui.common.DateField
import com.jeremy.flaghours.ui.common.HoursField
import com.jeremy.flaghours.ui.common.IntField
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.fmtDate
import com.jeremy.flaghours.ui.common.fmtPeriod
import com.jeremy.flaghours.ui.common.hrs

/** Full-screen editor for one labor line. */
@Composable
fun JobEditorDialog(
    initial: Job,
    ro: RepairOrder,
    plan: PayPlan,
    allJobs: List<JobWithRo>,
    onSave: (Job) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var job by remember { mutableStateOf(initial) }
    var pickComeback by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val original = job.comebackOfJobId?.let { id -> allJobs.firstOrNull { it.job.id == id } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            ScreenScaffold(title = if (initial.id == 0L) "Add line" else "Edit line", onBack = onDismiss) { pad ->
                Column(
                    Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DescriptionField(
                        job.description, { job = job.copy(description = it) }, ro.make, ro.model,
                        onPick = { job = job.copy(description = it.description, flaggedHundredths = it.flaggedHundredths) },
                    )
                    ChoiceChips(LaborType.entries, job.laborType, { it.label }, { job = job.copy(laborType = it) })
                    HoursField(job.flaggedHundredths.takeIf { initial.id != 0L || it != 0 }, { job = job.copy(flaggedHundredths = it ?: 0) }, "Flagged hours (as on the RO)")
                    HoursField(job.adjustedHundredths, { job = job.copy(adjustedHundredths = it) }, "Adjusted hours (warranty cut, optional)")
                    job.adjustedHundredths?.let { adj ->
                        val diff = job.flaggedHundredths - adj
                        Text(
                            if (diff > 0) "Cut by ${hrs(diff, plan.decimals)} h" else if (diff < 0) "Added ${hrs(-diff, plan.decimals)} h" else "No change",
                            color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IntField(job.mySplitPct, { job = job.copy(mySplitPct = (it ?: 0).coerceIn(0, 100)) }, "My split", suffix = "%")
                    DateField(job.workDate, { job = job.copy(workDate = it) }, "Work date")

                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    ChoiceChips(JobStatus.entries, job.status, { it.label }, { job = job.copy(status = it) })
                    val paidText = job.paidPeriodStart?.let { "Paid in period ${fmtPeriod(PayPeriod.startingOn(it, plan))}" }
                        ?: "Pay period set automatically (${plan.flagsPayWhen.label.lowercase()})"
                    Text(paidText, style = MaterialTheme.typography.bodyMedium)
                    if (job.paidPeriodStart != null) {
                        DateField(job.paidPeriodStart!!, { job = job.copy(paidPeriodStart = PayPeriod.forDate(it, plan).start) }, "Move to the pay period containing")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("This is a comeback", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(job.comebackOfJobId != null, { on -> if (on) pickComeback = true else job = job.copy(comebackOfJobId = null) })
                    }
                    if (original != null) {
                        Text("Comeback of RO ${original.roNumber}: ${original.job.description} (${fmtDate(original.job.workDate)}). Rule: ${plan.comebackRule.label}.")
                        TextButton(onClick = { pickComeback = true }) { Text("Change original job") }
                    }

                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    BigButton("Save line", onClick = {
                        when {
                            job.description.isBlank() -> error = "Enter a description."
                            job.flaggedHundredths <= 0 && job.comebackOfJobId == null -> error = "Enter the flagged hours."
                            else -> onSave(job.copy(description = job.description.trim()))
                        }
                    }, modifier = Modifier.fillMaxWidth())
                    if (onDelete != null) {
                        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Delete line", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (pickComeback) {
        val candidates = allJobs.filter { it.job.id != job.id && it.job.comebackOfJobId == null }
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pickComeback = false },
            title = { Text("Which job came back?") },
            text = {
                Column {
                    OutlinedTextField(q, { q = it }, label = { Text("Search RO # or description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val shown = candidates.filter {
                        q.isBlank() || it.roNumber.contains(q, true) || it.job.description.contains(q, true)
                    }.sortedByDescending { if (it.make == ro.make && it.model == ro.model && ro.make != null) 1 else 0 }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.job.id }) { c ->
                            ClickableRow(onClick = { job = job.copy(comebackOfJobId = c.job.id); pickComeback = false }) {
                                Column {
                                    Text("RO ${c.roNumber} · ${c.job.description}")
                                    Text(
                                        "${fmtDate(c.job.workDate)} · ${hrs(c.job.flaggedHundredths, plan.decimals)} h" +
                                            listOfNotNull(c.year?.toString(), c.make, c.model).joinToString(" ", prefix = " · "),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickComeback = false }) { Text("Cancel") } },
        )
    }
}
