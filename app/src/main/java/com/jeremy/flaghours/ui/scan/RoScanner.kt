package com.jeremy.flaghours.ui.scan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jeremy.flaghours.domain.RoTextParser
import com.jeremy.flaghours.ui.ro.JobDraft
import com.jeremy.flaghours.ui.ro.RoDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device OCR (bundled ML Kit model, works offline) + RoTextParser → editable draft. */
object RoScanner {

    fun photosDir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(context: Context): File = File(photosDir(context), "ro-${System.currentTimeMillis()}.jpg")

    suspend fun copyIntoPhotos(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val f = newPhotoFile(context)
        context.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        f
    }

    suspend fun recognize(context: Context, file: File): String {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, Uri.fromFile(file)) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(linesInReadingOrder(it)) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            recognizer.close()
        }
    }

    /**
     * ML Kit returns blocks, not rows. Rebuild rows by grouping lines whose vertical centers are close, then
     * reading left to right, so "Replace pads ........ 1.6" stays on one line.
     */
    private fun linesInReadingOrder(text: com.google.mlkit.vision.text.Text): String {
        val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        if (lines.isEmpty()) return text.text
        val sorted = lines.sortedBy { it.boundingBox!!.centerY() }
        val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
        for (l in sorted) {
            val box = l.boundingBox!!
            val row = rows.lastOrNull()
            val ref = row?.first()?.boundingBox
            if (ref != null && kotlin.math.abs(box.centerY() - ref.centerY()) < maxOf(ref.height(), box.height()) / 2) row.add(l) else rows.add(mutableListOf(l))
        }
        return rows.joinToString("\n") { r -> r.sortedBy { it.boundingBox!!.left }.joinToString("  ") { it.text } }
    }

    fun draftFrom(text: String, photo: File, today: LocalDate): RoDraft {
        val p = RoTextParser.parse(text)
        return RoDraft(
            roNumber = p.roNumber ?: "",
            openedDate = today,
            vin = p.vin ?: "",
            year = p.year?.toString() ?: "",
            make = p.make ?: "",
            model = p.model ?: "",
            photoUri = Uri.fromFile(photo).toString(),
            jobs = p.lines.mapIndexed { i, l -> JobDraft(key = i + 1L, description = l.description, laborType = l.laborType, hours = l.hoursHundredths) }
                .ifEmpty { listOf(JobDraft(key = 1)) },
        )
    }
}
