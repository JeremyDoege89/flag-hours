package com.jeremy.flaghours.ui.ro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jeremy.flaghours.data.db.JobTemplate
import com.jeremy.flaghours.domain.Units
import com.jeremy.flaghours.ui.common.app
import kotlinx.coroutines.delay

/** Job description with big tap-to-fill suggestions from past jobs (JobTemplate). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DescriptionField(
    value: String,
    onChange: (String) -> Unit,
    make: String?,
    model: String?,
    onPick: (JobTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = app().repository
    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<JobTemplate>()) }
    LaunchedEffect(value, focused) {
        if (!focused) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        suggestions = repo.searchTemplates(value, make, model).filter { it.description != value }
    }
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("Job description") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { t ->
                    SuggestionChip(
                        onClick = { onPick(t); suggestions = emptyList() },
                        label = { Text("${t.description} · ${Units.formatHours(t.flaggedHundredths, 2)} h", Modifier.padding(vertical = 8.dp)) },
                    )
                }
            }
        }
    }
}
