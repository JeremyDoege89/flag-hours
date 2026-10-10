package com.jeremy.flaghours.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jeremy.flaghours.ui.common.BigButton
import com.jeremy.flaghours.ui.common.ScreenScaffold
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.common.onMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File

@Composable
fun ScanScreen(onBack: () -> Unit, onDraftReady: () -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    val application = app()
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it; asked = true }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    fun process(file: File) {
        working = true
        error = null
        scope.launch {
            try {
                val text = RoScanner.recognize(context, file)
                application.pendingDraft = RoScanner.draftFrom(text, file, application.repository.today())
                onMain(onDraftReady)
            } catch (e: Exception) {
                working = false
                error = "Couldn't read text from that photo. Try again with more light, or enter it manually."
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { runCatching { RoScanner.copyIntoPhotos(context, uri) }.onSuccess { process(it) }.onFailure { error = "Couldn't open that image." } }
    }

    ScreenScaffold(title = "Scan RO", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fill the frame with the RO's labor section, hold steady, and tap Capture. You'll check everything before it's saved.", style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    working -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Reading the RO…")
                    }
                    hasPermission -> CameraCapture(onCaptured = ::process, onError = { error = it })
                    asked -> Text("Camera permission is off. You can still pick a photo or enter the RO by hand.")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    enabled = !working,
                ) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Text(" Pick photo")
                }
                OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f).heightIn(min = 56.dp), enabled = !working) { Text("Enter manually") }
            }
        }
    }
}

@Composable
private fun CameraCapture(onCaptured: (File) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val future = ProcessCameraProvider.getInstance(context)
            val provider = suspendCancellableCoroutine { cont ->
                future.addListener({ runCatching { future.get() }.onSuccess { cont.resume(it) }.onFailure { cont.resumeWithException(it) } }, ContextCompat.getMainExecutor(context))
            }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        } catch (e: Exception) {
            onError("Couldn't start the camera.")
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(
                    onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) { Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flashlight") }
            }
        }
        BigButton(
            "Capture",
            icon = Icons.Default.CameraAlt,
            enabled = !capturing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            onClick = {
                capturing = true
                val file = RoScanner.newPhotoFile(context)
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            capturing = false
                            onCaptured(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            capturing = false
                            onError("Couldn't take the photo.")
                        }
                    },
                )
            },
        )
    }
}
