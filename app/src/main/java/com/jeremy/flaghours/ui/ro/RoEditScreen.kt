package com.jeremy.flaghours.ui.ro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.data.db.RepairOrder
import com.jeremy.flaghours.domain.LaborType
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.ChoiceChips
import com.jeremy.flaghours.ui.common.DateField
import com.jeremy.flaghours.ui.common.HoursField
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.onMain
import kotlinx.coroutines.launch

/**
 * New RO (header + lines) or edit an existing RO's header. [fromDraft] loads the scan result.
 * [onSaved] receives the RO id.
 */
@Composable
fun RoEditScreen(roId: Long, fromDraft: Boolean, onBack: () -> Unit, onSaved: (Long) -> Unit, onOpenExisting: (Long) -> Unit) {
    val application = app()
    val repo = application.repository
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<RoDraft?>(null) }
    var existing by remember { mutableStateOf<RepairOrder?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var duplicateId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(roId) {
        draft = when {
            roId != 0L -> repo.getRo(roId)?.let { existing = it; RoDraft.from(it) }
            fromDraft -> application.pendingDraft.also { application.pendingDraft = null } ?: RoDraft(openedDate = repo.today())
            else -> RoDraft(openedDate = repo.today())
        }
    }
    val d = draft ?: return
    val isNew = roId == 0L

    fun save() {
        if (d.roNumber.isBlank()) {
            error = "Enter the RO number."
            return
        }
        if (isNew && d.jobs.any { it.description.isNotBlank() && it.hours == null }) {
            error = "Every line needs flagged hours."
            return
        }
        scope.launch {
            if (isNew) {
                val dup = repo.findRoByNumber(d.roNumber)
                if (dup != null) {
                    error = "RO ${d.roNumber} already exists."
                    duplicateId = dup.id
                    return@launch
                }
                val id = repo.createRoWithJobs(d.toRepairOrder(), d.toJobs(repo.today()))
                onMain { onSaved(id) }
            } else {
                repo.saveRo(d.toRepairOrder(roId).copy(photoUri = existing?.photoUri ?: d.photoUri))
                onMain { onSaved(roId) }
            }
        }
    }

    ScreenScaffold(title = if (isNew) "New RO" else "Edit RO ${existing?.roNumber ?: ""}", onBack = onBack) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (fromDraft) {
                Text("Check everything against the RO before saving. The RO is the source of truth.", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                d.roNumber, { draft = d.copy(roNumber = it) }, label = { Text("RO number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(d.openedDate, { draft = d.copy(openedDate = it) }, "RO date")
            OutlinedTextField(
                d.vin, { draft = d.copy(vin = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(17)) },
                label = { Text("VIN (optional)") }, singleLine = true,
                supportingText = { Text("${d.vin.length}/17") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    d.year, { draft = d.copy(year = it.filter(Char::isDigit).take(4)) }, label = { Text("Year") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(d.make, { draft = d.copy(make = it) }, label = { Text("Make") }, singleLine = true, modifier = Modifier.weight(1.5f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(d.model, { draft = d.copy(model = it) }, label = { Text("Model") }, singleLine = true, modifier = Modifier.weight(1.5f))
                OutlinedTextField(d.engine, { draft = d.copy(engine = it) }, label = { Text("Engine") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(d.notes, { draft = d.copy(notes = it) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            if (isNew) {
                Text("Labor lines", style = MaterialTheme.typography.titleMedium)
                d.jobs.forEachIndexed { i, line ->
                    key(line.key) {
                        JobDraftCard(
                            line = line,
                            make = d.make.ifBlank { null },
                            model = d.model.ifBlank { null },
                            onChange = { nl -> draft = d.copy(jobs = d.jobs.mapIndexed { j, x -> if (j == i) nl else x }) },
                            onRemove = { draft = d.copy(jobs = d.jobs.filterIndexed { j, _ -> j != i }) },
                        )
                    }
                }
                OutlinedButton(
                    onClick = { draft = d.copy(jobs = d.jobs + JobDraft(key = (d.jobs.maxOfOrNull { it.key } ?: 0) + 1)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null)
                    Text("Add line")
                }
                Text("Lines are saved as Open with today's work date. Change status, split or adjustments on the RO screen.", style = MaterialTheme.typography.bodyMedium)
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                duplicateId?.let { id -> TextButton(onClick = { onOpenExisting(id) }) { Text("Open existing RO") } }
            }
            BigButton("Save RO", onClick = ::save, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun JobDraftCard(line: JobDraft, make: String?, model: String?, onChange: (JobDraft) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                DescriptionField(
                    line.description, { onChange(line.copy(description = it)) }, make, model,
                    onPick = { onChange(line.copy(description = it.description, hours = it.flaggedHundredths)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove line") }
            }
            HoursField(line.hours, { onChange(line.copy(hours = it)) }, "Flagged hours")
            ChoiceChips(LaborType.entries, line.laborType, { it.label }, { onChange(line.copy(laborType = it)) })
        }
    }
}
