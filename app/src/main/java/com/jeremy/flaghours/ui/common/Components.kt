package com.jeremy.flaghours.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.Units
import com.jeremy.flaghours.ui.theme.BigNumber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** True when the screen is shown above the bottom navigation bar (which then owns the bottom inset). */
val LocalHasBottomBar = compositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    snackbar: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = { actions() },
            )
        },
        floatingActionButton = fab,
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        contentWindowInsets = if (LocalHasBottomBar.current) WindowInsets(0) else WindowInsets.navigationBars,
        content = content,
    )
}

/** A tall, glove-friendly button. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 64.dp), enabled = enabled, colors = colors) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, highlight: Boolean = false) {
    Card(
        modifier = modifier,
        colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = BigNumber)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun PeriodBar(period: PayPeriod, onPrev: () -> Unit, onNext: () -> Unit, onToday: (() -> Unit)? = null, label: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.ChevronLeft, "Previous period") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmtPeriod(period), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (label != null) Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (onToday != null) TextButton(onClick = onToday) { Text("Now") }
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.ChevronRight, "Next period") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o), Modifier.padding(vertical = 10.dp)) },
            )
        }
    }
}

/** Text field for money; keeps the typed text and reports the parsed cents (null if blank/invalid). */
@Composable
fun MoneyField(cents: Long?, onChange: (Long?) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String? = null) {
    var text by remember { mutableStateOf(moneyText(cents)) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(Units.parseMoney(it)) },
        label = { Text(label) },
        prefix = { Text("$") },
        placeholder = placeholder?.let { { Text(it) } },
        isError = text.isNotBlank() && Units.parseMoney(text) == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Text field for hours; keeps the typed text and reports parsed hundredths (null if blank/invalid). */
@Composable
fun HoursField(hundredths: Int?, onChange: (Int?) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String = "h") {
    var text by remember { mutableStateOf(hoursText(hundredths)) }
    LaunchedEffect(hundredths) {
        // Follow external changes (e.g. autocomplete) without clobbering what's being typed.
        if (Units.parseHours(text) != hundredths) text = hoursText(hundredths)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(Units.parseHours(it)) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        isError = text.isNotBlank() && Units.parseHours(text) == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun IntField(value: Int?, onChange: (Int?) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String? = null) {
    var text by remember { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { s -> text = s.filter { it.isDigit() }.take(6); onChange(text.toIntOrNull()) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Read-only field that opens a date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    OutlinedTextField(
        value = fmtDate(date),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Default.Event, null) },
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth(),
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(it / DAY_MS)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** Date + time picker pair for shift editing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(value: LocalDateTime, onChange: (LocalDateTime) -> Unit, label: String, modifier: Modifier = Modifier) {
    var pickTime by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DateField(value.toLocalDate(), { onChange(LocalDateTime.of(it, value.toLocalTime())) }, "Date", Modifier.weight(1f))
            Button(onClick = { pickTime = true }, modifier = Modifier.heightIn(min = 56.dp)) { Text(fmtTime(value)) }
        }
    }
    if (pickTime) {
        val state = rememberTimePickerState(value.hour, value.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalDateTime.of(value.toLocalDate(), LocalTime.of(state.hour, state.minute)))
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(24.dp),
    )
}

@Composable
fun ClickableRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

private const val DAY_MS = 86_400_000L
