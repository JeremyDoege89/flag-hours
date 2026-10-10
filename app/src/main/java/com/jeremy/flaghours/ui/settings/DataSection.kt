package com.jeremy.flaghours.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.jeremy.flaghours.domain.Backup
import com.jeremy.flaghours.domain.CsvExporter
import com.jeremy.flaghours.domain.CsvImportException
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.SectionCard
import com.jeremy.flaghours.ui.common.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** CSV backup: share, save to a file, or restore. */
@Composable
fun DataSection(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val repo = app().repository
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Backup?>(null) }
    val fileName = "flag-hours-${LocalDate.now()}.csv"

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val csv = repo.exportCsv()
                    withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } }
                }.isSuccess
                snackbar.showSnackbar(if (ok) "Backup saved" else "Couldn't save the file")
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                    }
                    pending = withContext(Dispatchers.Default) { CsvExporter.import(text) }
                } catch (e: CsvImportException) {
                    snackbar.showSnackbar(e.message ?: "Couldn't read that file")
                } catch (e: Exception) {
                    snackbar.showSnackbar("Couldn't read that file")
                }
            }
        }
    }

    SectionCard("Backup (CSV)") {
        Text("One CSV file with all ROs, jobs, shifts, hourly time, pay stubs and your pay plan. Opens in any spreadsheet.", style = MaterialTheme.typography.bodyMedium)
        BigButton("Share backup", onClick = {
            scope.launch {
                val csv = repo.exportCsv()
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "exports").apply { mkdirs() }.resolve(fileName).apply { writeText(csv) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share backup"))
            }
        }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { saveLauncher.launch(fileName) }, modifier = Modifier.fillMaxWidth()) { Text("Save backup to a file") }
        OutlinedButton(onClick = { openLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Restore from backup")
        }
    }

    pending?.let { b ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "The backup has ${b.ros.size} ROs, ${b.jobs.size} jobs, ${b.shifts.size} shifts, ${b.hourly.size} hourly entries and " +
                        "${b.stubs.size} pay stubs. Everything in the app now will be replaced. Export a backup first if you're unsure.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.replaceAllWith(b)
                        pending = null
                        snackbar.showSnackbar("Backup restored")
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
