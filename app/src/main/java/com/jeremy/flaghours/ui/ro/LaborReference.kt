package com.jeremy.flaghours.ui.ro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.data.db.Job
import com.jeremy.flaghours.data.remote.OlpError
import com.jeremy.flaghours.data.remote.OlpResult
import com.jeremy.flaghours.data.remote.OlpService
import com.jeremy.flaghours.data.remote.VehicleLoad
import com.jeremy.flaghours.domain.JobMatch
import com.jeremy.flaghours.domain.JobMatcher
import com.jeremy.flaghours.domain.LaborRange
import com.jeremy.flaghours.domain.LaborRow
import com.jeremy.flaghours.domain.RangeFit
import com.jeremy.flaghours.domain.VehicleOption
import com.jeremy.flaghours.ui.common.hrs
import com.jeremy.flaghours.ui.theme.StatusColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun rangeText(low: Int?, high: Int?, hours: Int?, decimals: Int): String = when {
    low != null && high != null && low != high -> "${hrs(low, decimals)}–${hrs(high, decimals)} h"
    else -> (hours ?: low ?: high)?.let { "${hrs(it, decimals)} h" } ?: ""
}

fun errorText(e: OlpError): String = when (e) {
    is OlpError.DailyLimit -> "Daily lookup limit reached. Resets ${e.resetAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE h:mm a"))} (midnight UTC)."
    else -> e.message
}

/** Vehicle-level status for reference labor times on the RO screen. */
@Composable
fun LaborRefPanel(
    hasKey: Boolean,
    vehicleLabel: String?,
    state: VehicleLoad?,
    loading: Boolean,
    quotaRemaining: Int?,
    onLoad: () -> Unit,
    onBrowse: () -> Unit,
    onManual: () -> Unit,
    onChangeEngine: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text("Typical labor times", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        when {
            !hasKey -> Text("Add your Open Labor Project API key in Settings to compare your flags with typical times.", style = MaterialTheme.typography.bodyMedium)
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text("Loading typical times…")
            }
            vehicleLabel == null -> {
                Text("Add the year, make and model (or fill them from the VIN) first.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onManual) { Text("Pick the vehicle from the catalog") }
            }
            state is VehicleLoad.Ready -> {
                Text(
                    "Loaded for $vehicleLabel" + (state.engine?.let { " · $it" } ?: "") + " · ${state.rows.map { it.jobSlug }.distinct().size} jobs",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row {
                    TextButton(onClick = onBrowse) { Text("Browse all jobs") }
                    if (state.engines.size > 1) TextButton(onClick = onChangeEngine) { Text("Change engine") }
                    TextButton(onClick = onManual) { Text("Wrong vehicle?") }
                }
            }
            state is VehicleLoad.Failed -> {
                Text(errorText(state.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Row {
                    if (state.error !is OlpError.DailyLimit && state.error !is OlpError.NoKey && state.error !is OlpError.BadKey) {
                        TextButton(onClick = onLoad) { Text("Try again") }
                    }
                    TextButton(onClick = onManual) { Text("Pick vehicle manually") }
                }
            }
            else -> {
                OutlinedButton(onClick = onLoad, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Load typical times for $vehicleLabel") }
                Text(
                    "Uses 1 lookup for this vehicle (up to 3 the first time for a make)" + (quotaRemaining?.let { ". $it left today." } ?: "."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Per-line reference: saved pick with a range badge, or suggested picks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobReference(job: Job, decimals: Int, suggestions: List<JobMatch>?, onPick: (LaborRow) -> Unit, onMore: () -> Unit) {
    val ref = job.refJob
    if (ref != null || job.refLowHundredths != null) {
        val fit = LaborRange.classify(job.flaggedHundredths, job.refLowHundredths, job.refHighHundredths)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fit != null) {
                val (color, label) = when (fit) {
                    RangeFit.IN_RANGE -> StatusColors.inRange to "In range"
                    RangeFit.BELOW -> StatusColors.belowRange to "Below range"
                    RangeFit.ABOVE -> StatusColors.aboveRange to "Above range"
                }
                Text(
                    label,
                    color = if (fit == RangeFit.BELOW) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(color, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(
                    listOfNotNull(ref, rangeText(job.refLowHundredths, job.refHighHundredths, job.refHoursHundredths, decimals).ifEmpty { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                job.refConfidence?.let { Text("$it confidence", style = MaterialTheme.typography.bodySmall) }
            }
            if (suggestions != null) TextButton(onClick = onMore) { Text("Change") }
        }
    } else if (suggestions != null) {
        if (suggestions.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("No matching job in the catalog.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onMore) { Text("Browse") }
            }
        } else {
            Text("Typical time — tap the matching job:", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(3).forEach { m ->
                    SuggestionChip(
                        onClick = { onPick(m.row) },
                        label = { Text("${m.row.job} · ${rangeText(m.row.lowHundredths, m.row.highHundredths, m.row.hoursHundredths, decimals)}", Modifier.padding(vertical = 8.dp)) },
                    )
                }
                TextButton(onClick = onMore) { Text("More…") }
            }
        }
    }
}

/** Search + category-grouped list of every cached job for the vehicle. [onPick] null = browse only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobBrowserSheet(rows: List<LaborRow>, initialQuery: String, decimals: Int, title: String, onPick: ((LaborRow) -> Unit)?, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf(initialQuery) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(q, { q = it }, label = { Text("Search jobs") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val shown: List<Pair<String?, LaborRow>> = if (q.isBlank()) {
                rows.sortedWith(compareBy({ it.category ?: "Other" }, { it.job })).map { it.category to it }
            } else {
                JobMatcher.match(q, rows, limit = 40, minScore = 30).map { null to it.row }
                    .ifEmpty { rows.filter { it.job.contains(q, true) || (it.category?.contains(q, true) == true) }.map { null to it } }
            }
            LazyColumn(Modifier.weight(1f)) {
                var lastCat: String? = "\u0000"
                shown.forEach { (cat, row) ->
                    if (q.isBlank() && cat != lastCat) {
                        val header = cat ?: "Other"
                        lastCat = cat
                        item(key = "h-$header") {
                            Text(header, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                    }
                    item(key = "r-${row.engine}-${row.jobSlug}") {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .then(if (onPick != null) Modifier.clickableRow { onPick(row) } else Modifier)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.job)
                                Text(
                                    listOfNotNull(if (q.isNotBlank()) row.category else null, row.engine.ifEmpty { null }, row.confidence).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(rangeText(row.lowHundredths, row.highHundredths, row.hoursHundredths, decimals), fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider()
                    }
                }
            }
            Text(OlpService.ATTRIBUTION, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

@Composable
fun OptionDialog(title: String, subtitle: String?, options: List<Pair<String, String>>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (options.size > 8) OutlinedTextField(filter, { filter = it }, label = { Text("Filter") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(options.filter { filter.isBlank() || it.second.contains(filter, true) || it.first.contains(filter, true) }, key = { it.first }) { (value, label) ->
                        Text(label, Modifier.fillMaxWidth().clickableRow { onPick(value) }.padding(vertical = 14.dp))
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Manual year/make/model picker driven by the cached catalog lists. */
@Composable
fun ManualVehicleDialog(
    olp: OlpService,
    initialYear: Int?,
    onPicked: (year: Int, make: VehicleOption, model: VehicleOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableStateOf(initialYear?.toString() ?: "") }
    var makes by remember { mutableStateOf<List<VehicleOption>?>(null) }
    var make by remember { mutableStateOf<VehicleOption?>(null) }
    var models by remember { mutableStateOf<List<VehicleOption>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        when (val r = olp.makes()) {
            is OlpResult.Ok -> makes = r.value
            is OlpResult.Err -> error = errorText(r.error)
        }
    }
    LaunchedEffect(make) {
        val m = make ?: return@LaunchedEffect
        models = null
        when (val r = olp.models(m.slug)) {
            is OlpResult.Ok -> models = r.value
            is OlpResult.Err -> error = errorText(r.error)
        }
    }
    val y = year.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (make == null) "Pick the make" else "Pick the ${make!!.name} model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val list = if (make == null) makes else models?.filter { y == null || it.coversYear(y) }
                if (list == null && error == null) CircularProgressIndicator()
                list?.let { opts ->
                    var filter by remember(make) { mutableStateOf("") }
                    OutlinedTextField(filter, { filter = it }, label = { Text("Filter") }, singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(opts.filter { filter.isBlank() || it.name.contains(filter, true) }, key = { it.slug }) { o ->
                            Text(
                                o.name,
                                Modifier.fillMaxWidth().clickableRow {
                                    if (make == null) make = o else if (y != null) onPicked(y, make!!, o) else error = "Enter the year first."
                                }.padding(vertical = 14.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = { if (make != null) TextButton(onClick = { make = null; models = null }) { Text("Back to makes") } },
    )
}
