package com.jeremy.flaghours.ui.ro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/** Thumbnail of the scanned RO photo; tap for a full-screen view. */
@Composable
fun RoPhoto(photoUri: String) {
    var full by remember { mutableStateOf(false) }
    val thumb by produceState<Bitmap?>(null, photoUri) { value = decode(photoUri, 800) }
    val b = thumb ?: return
    Image(
        b.asImageBitmap(), "RO photo",
        modifier = Modifier.fillMaxWidth().height(180.dp).clickable { full = true },
        contentScale = ContentScale.Crop,
    )
    if (full) {
        val big by produceState<Bitmap?>(null, photoUri) { value = decode(photoUri, 2400) }
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            (big ?: b).let { Image(it.asImageBitmap(), "RO photo", Modifier.fillMaxSize().clickable { full = false }, contentScale = ContentScale.Fit) }
        }
    }
}

private suspend fun decode(photoUri: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val path = File(URI(photoUri)).path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
