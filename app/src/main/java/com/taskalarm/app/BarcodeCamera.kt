package com.taskalarm.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
internal fun BarcodeCamera(onRead: (String) -> Unit, onCancel: () -> Unit) {
  val context = LocalContext.current
  val owner = LocalLifecycleOwner.current
  var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
  var error by remember { mutableStateOf("") }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
  val executor = remember { Executors.newSingleThreadExecutor() }
  val scanner = remember { BarcodeScanning.getClient() }
  val provider = remember { ProcessCameraProvider.getInstance(context) }
  val closed = remember { AtomicBoolean(false) }
  val accepted = remember { AtomicBoolean(false) }
  val currentRead by rememberUpdatedState(onRead)
  DisposableEffect(owner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_START) (context as? MainActivity)?.setBarcodeScanning(true)
      if (event == Lifecycle.Event.ON_STOP) (context as? MainActivity)?.setBarcodeScanning(false)
    }
    owner.lifecycle.addObserver(observer)
    if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) (context as? MainActivity)?.setBarcodeScanning(true)
    onDispose {
      owner.lifecycle.removeObserver(observer)
      closed.set(true)
      if (provider.isDone) runCatching { provider.get().unbindAll() }
      scanner.close(); executor.shutdown()
      (context as? MainActivity)?.setBarcodeScanning(false)
    }
  }
  TaskAlarmTheme(dark = true) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
      if (allowed) AndroidView(modifier = Modifier.fillMaxSize(), factory = { viewContext ->
        PreviewView(viewContext).also { view ->
          provider.addListener({
            if (!closed.get()) try {
              val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
              val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
              analysis.setAnalyzer(executor) { image ->
                val media = image.image
                if (media == null || closed.get() || accepted.get()) image.close()
                else scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
                  .addOnSuccessListener { results ->
                    val value = results.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotEmpty) }
                    if (value != null && !closed.get() && accepted.compareAndSet(false, true)) currentRead(value)
                  }
                  .addOnFailureListener { error = "Could not read this barcode. Try again." }
                  .addOnCompleteListener { image.close() }
              }
              provider.get().bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (failure: Exception) { error = failure.message ?: "Camera unavailable" }
          }, ContextCompat.getMainExecutor(viewContext))
        }
      })
      Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          FilledTonalIconButton(onClick = onCancel) { Ico(R.drawable.ic_close, "Cancel scan") }
          Text("Scan a barcode", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(start = 12.dp))
        }
        Spacer(Modifier.weight(1f))
        if (allowed) {
          Box(Modifier.size(260.dp).border(3.dp, Color.White.copy(alpha = 0.9f), MaterialTheme.shapes.large))
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
          IconBadge(R.drawable.ic_qr_code_scanner, size = 96.dp)
          Text("Camera access is needed to scan", style = MaterialTheme.typography.titleMedium, color = Color.White)
          Text("If you denied it permanently, turn on Camera in Settings → Apps → Task Alarm → Permissions.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
          Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = MaterialTheme.shapes.medium, color = Color.Black.copy(alpha = 0.6f)) {
          Text(error.ifEmpty { "Point the camera at a product barcode or QR code." }, style = MaterialTheme.typography.bodyLarge,
            color = if (error.isEmpty()) Color.White else MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
      }
    }
  }
}
