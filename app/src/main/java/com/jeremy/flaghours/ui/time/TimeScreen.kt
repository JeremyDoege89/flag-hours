package com.jeremy.flaghours.ui.time

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jeremy.flaghours.data.FlagHoursRepository
import com.jeremy.flaghours.data.db.HourlyEntry
import com.jeremy.flaghours.data.db.Shift
import com.jeremy.flaghours.domain.PayCalculator
import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.Units
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.ChoiceChips
import com.jeremy.flaghours.ui.common.DateField
import com.jeremy.flaghours.ui.common.DateTimeField
import com.jeremy.flaghours.ui.common.FORGOTTEN_SHIFT_HOURS
import com.jeremy.flaghours.ui.common.HoursField
import com.jeremy.flaghours.ui.common.PeriodBar
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.fmtDate
import com.jeremy.flaghours.ui.common.fmtDateTime
import com.jeremy.flaghours.ui.common.fmtTime
import com.jeremy.flaghours.ui.common.hrs
import com.jeremy.flaghours.ui.common.minutesLabel
import com.jeremy.flaghours.ui.common.rememberLiveData
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun TimeScreen(onSettings: () -> Unit) {
    val repo = app().repository
    val scope = rememberCoroutineScope()
    val live = rememberLiveData(repo)
    val plan = live.plan
    val open by remember { repo.openShift }.collectAsState(initial = null)
    var periodDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val period = PayPeriod.forDate(LocalDate.ofEpochDay(periodDay), plan)
    var editingShift by remember { mutableStateOf<Shift?>(null) }
    var editingHourly by remember { mutableStateOf<HourlyEntry?>(null) }

    val shiftsInPeriod = live.shifts.filter { s ->
        val end = s.end ?: live.now
        end.isAfter(period.startTime) && s.start.isBefore(period.endTimeExclusive)
    }
    val hourlyInPeriod = live.hourly.filter { it.date in period }
    val clocked = PayCalculator.clockedMinutes(live.shifts.map { it.toSpan() }, period, live.now)

    ScreenScaffold(title = "Time", actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ClockCard(open, live.now, repo, onFix = { editingShift = it }) }
            item {
                PeriodBar(
                    period,
                    onPrev = { periodDay = PayPeriod.previous(period, plan).start.toEpochDay() },
                    onNext = { periodDay = PayPeriod.next(period, plan).start.toEpochDay() },
                    onToday = { periodDay = LocalDate.now().toEpochDay() },
                    label = "Clocked ${minutesLabel(clocked)} (${hrs(Units.minutesToHundredths(clocked), plan.decimals)} h)",
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Shifts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val start = period.start.atTime(8, 0).coerceAtMost(live.now)
                        editingShift = Shift(start = start, end = start.plusHours(8))
                    }) {
                        Icon(Icons.Default.Add, null)
                        Text("Add shift")
                    }
                }
            }
            if (shiftsInPeriod.isEmpty()) item { Text("No shifts this period.", style = MaterialTheme.typography.bodyMedium) }
            items(shiftsInPeriod, key = { "s${it.id}" }) { s ->
                val end = s.end
                val mins = Duration.between(s.start, end ?: live.now).toMinutes()
                Card(onClick = { editingShift = s }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fmtDate(s.start.toLocalDate()), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${fmtTime(s.start)} – ${end?.let { if (it.toLocalDate() != s.start.toLocalDate()) fmtDateTime(it) else fmtTime(it) } ?: "now"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(minutesLabel(mins), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hourly time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editingHourly = HourlyEntry(date = repo.today().coerceIn(period.start, period.end), minutes = 60, reason = REASONS.first()) }) {
                        Icon(Icons.Default.Add, null)
                        Text("Add")
                    }
                }
                Text("Training, shop time or diag paid by the clock at your hourly rate.", style = MaterialTheme.typography.bodyMedium)
            }
            items(hourlyInPeriod, key = { "h${it.id}" }) { h ->
                Card(onClick = { editingHourly = h }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.reason, style = MaterialTheme.typography.titleMedium)
                            Text(fmtDate(h.date), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(minutesLabel(h.minutes.toLong()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    editingShift?.let { s ->
        ShiftDialog(
            s,
            onSave = { scope.launch { repo.saveShift(it); editingShift = null } },
            onDelete = if (s.id != 0L) ({ scope.launch { repo.deleteShift(s); editingShift = null } }) else null,
            onDismiss = { editingShift = null },
            now = live.now,
        )
    }
    editingHourly?.let { h ->
        HourlyDialog(
            h,
            onSave = { scope.launch { repo.saveHourly(it); editingHourly = null } },
            onDelete = if (h.id != 0L) ({ scope.launch { repo.deleteHourly(h); editingHourly = null } }) else null,
            onDismiss = { editingHourly = null },
        )
    }
}

private val REASONS = listOf("Training", "Shop time", "Diag by clock", "Other")

/** Big clock in/out control, also used on the dashboard. */
@Composable
fun ClockCard(open: Shift?, now: LocalDateTime, repo: FlagHoursRepository, onFix: (Shift) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (open == null) {
            BigButton("Clock in", onClick = { scope.launch { repo.clockIn() } }, icon = Icons.Default.Login, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
        } else {
            val mins = Duration.between(open.start, now).toMinutes()
            val forgotten = mins >= FORGOTTEN_SHIFT_HOURS * 60
            if (forgotten) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Clocked in since ${fmtDateTime(open.start)} (${minutesLabel(mins)}). Forgot to clock out?", color = MaterialTheme.colorScheme.onErrorContainer)
                        OutlinedButton(onClick = { onFix(open.copy(end = open.start.plusHours(8).coerceAtMost(now))) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Fix clock-out time")
                        }
                    }
                }
            }
            Text("On the clock since ${fmtTime(open.start)} · ${minutesLabel(mins)}", style = MaterialTheme.typography.titleMedium)
            BigButton(
                "Clock out",
                onClick = { scope.launch { repo.clockOut() } },
                icon = Icons.Default.Logout,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            )
        }
    }
}

@Composable
fun ShiftDialog(shift: Shift, onSave: (Shift) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit, now: LocalDateTime) {
    var s by remember { mutableStateOf(shift) }
    val end = s.end
    val error = when {
        end != null && !end.isAfter(s.start) -> "End must be after start."
        s.start.isAfter(now) -> "Start can't be in the future."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (shift.id == 0L) "Add shift" else "Edit shift") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateTimeField(s.start, { s = s.copy(start = it) }, "Start")
                if (end != null) {
                    DateTimeField(end, { s = s.copy(end = it) }, "End")
                    Text("Length ${minutesLabel(Duration.between(s.start, end).toMinutes().coerceAtLeast(0))}")
                } else {
                    Text("Still on the clock.")
                    OutlinedButton(onClick = { s = s.copy(end = now) }) { Text("Set an end time") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete shift", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(s) }, enabled = error == null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HourlyDialog(entry: HourlyEntry, onSave: (HourlyEntry) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var e by remember { mutableStateOf(entry) }
    var custom by remember { mutableStateOf(if (entry.reason in REASONS) "" else entry.reason) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.id == 0L) "Add hourly time" else "Edit hourly time") },
        text = {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(e.date, { e = e.copy(date = it) }, "Date")
                HoursField(Units.minutesToHundredths(e.minutes.toLong()), { h -> e = e.copy(minutes = Units.hundredthsToMinutes(h ?: 0).toInt()) }, "Hours")
                Text("= ${minutesLabel(e.minutes.toLong())}", style = MaterialTheme.typography.bodyMedium)
                ChoiceChips(REASONS, if (e.reason in REASONS) e.reason else "Other", { it }, { e = e.copy(reason = if (it == "Other") custom.ifBlank { "Other" } else it) })
                if (e.reason !in REASONS.dropLast(1)) {
                    OutlinedTextField(custom, { custom = it; e = e.copy(reason = it.ifBlank { "Other" }) }, label = { Text("Reason") }, singleLine = true)
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(e) }, enabled = e.minutes > 0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shift editor wired to the repository (used for forgotten clock-outs on the dashboard). */
@Composable
fun FixShiftDialog(shift: Shift, now: LocalDateTime, onDone: () -> Unit) {
    val repo = app().repository
    val scope = rememberCoroutineScope()
    ShiftDialog(shift, onSave = { scope.launch { repo.saveShift(it); onDone() } }, onDelete = null, onDismiss = onDone, now = now)
}
