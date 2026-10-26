package com.heavy.photoediting.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.heavy.photoediting.engine.Storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

@Composable
fun CameraScreen(onCaptured: (Uri) -> Unit, onPickGallery: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) perm.launch(Manifest.permission.CAMERA) }
    if (granted) CameraContent(onCaptured, onPickGallery, onClose)
    else Column(
        Modifier.fillMaxSize().background(HColors.Bg).systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text("Camera access is needed to take photos.\nEditing imported photos works without it.",
            color = HColors.Text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { perm.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        TextButton(onClick = onClose) { Text("Back") }
    }
}

private suspend fun android.content.Context.cameraProvider(): ProcessCameraProvider = suspendCoroutine { c ->
    val f = ProcessCameraProvider.getInstance(this)
    f.addListener({ c.resume(f.get()) }, ContextCompat.getMainExecutor(this))
}

@Composable
private fun CameraContent(onCaptured: (Uri) -> Unit, onPickGallery: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZ by remember { mutableFloatStateOf(1f) }
    var maxZ by remember { mutableFloatStateOf(1f) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var busy by remember { mutableStateOf(false) }
    var whiteFlash by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(front) {
        val p = ctx.cameraProvider(); provider = p
        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val ic = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)   // highest-resolution JPEG
            .setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
        val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            p.unbindAll()
            val cam = p.bindToLifecycle(owner, sel, preview, ic)
            camera = cam; capture = ic
            val zs = cam.cameraInfo.zoomState.value
            minZ = zs?.minZoomRatio ?: 1f; maxZ = min(zs?.maxZoomRatio ?: 1f, 10f); zoom = 1f
        } catch (e: Exception) {
            Toast.makeText(ctx, "Camera unavailable", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(flash, capture, front) {
        capture?.flashMode = if (front) ImageCapture.FLASH_MODE_OFF else flash
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    fun takeNow() {
        val ic = capture ?: return
        val opts = ImageCapture.OutputFileOptions.Builder(
            ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Storage.newValues("HEAVY")
        ).setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = front }).build()
        ic.takePicture(opts, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                busy = false; whiteFlash = false
                r.savedUri?.let(onCaptured)
            }
            override fun onError(e: ImageCaptureException) {
                busy = false; whiteFlash = false
                Toast.makeText(ctx, "Capture failed", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun shutter() {
        if (busy) return
        busy = true
        scope.launch {
            for (i in timer downTo 1) { countdown = i; delay(1000) }
            countdown = 0
            if (front && flash != ImageCapture.FLASH_MODE_OFF) { whiteFlash = true; delay(250) } // screen flash
            takeNow()
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                flash = when (flash) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            }) {
                Icon(when (flash) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                    else -> Icons.Rounded.FlashOff
                }, null, tint = Color.White)
            }
            Pill(if (timer == 0) "Timer off" else "${timer}s", timer != 0) { timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 } }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp))) {
            AndroidView({ previewView }, Modifier.fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { off ->
                        camera?.let { cam ->
                            val pt = previewView.meteringPointFactory.createPoint(off.x, off.y)
                            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(pt).build())
                        }
                    }
                }
                .pointerInput(camera) {
                    detectTransformGestures { _, _, z, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        zoom = (zoom * z).coerceIn(minZ, maxZ)
                        cam.cameraControl.setZoomRatio(zoom)
                    }
                })
            if (countdown > 0) Text("$countdown", Modifier.align(Alignment.Center), color = Color.White,
                fontSize = 96.sp, fontWeight = FontWeight.Bold)
            if (whiteFlash) Box(Modifier.fillMaxSize().background(Color.White))
            Row(Modifier.align(Alignment.BottomCenter).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 1f, 2f, 3f).filter { it >= minZ - 0.01f && it <= maxZ + 0.01f }.forEach { z ->
                    val sel = kotlin.math.abs(zoom - z) < 0.15f
                    Box(Modifier.size(38.dp).clip(CircleShape).background(if (sel) HColors.Violet else Color(0x88000000))
                        .clickable { zoom = z; camera?.cameraControl?.setZoomRatio(z) }, contentAlignment = Alignment.Center) {
                        Text(if (z < 1f) "0.5x" else "${z.toInt()}x", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp, start = 28.dp, end = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(HColors.Surface).clickable(onClick = onPickGallery),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(80.dp).clip(CircleShape).border(BorderStroke(4.dp, HColors.Violet), CircleShape).padding(8.dp)
                .clip(CircleShape).background(if (busy) HColors.Muted else Color.White).clickable(enabled = !busy) { shutter() })
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(52.dp).clip(CircleShape).background(HColors.Surface).clickable { front = !front },
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.FlipCameraAndroid, null, tint = Color.White) }
        }
    }
}
