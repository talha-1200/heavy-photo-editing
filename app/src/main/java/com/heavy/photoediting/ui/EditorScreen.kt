package com.heavy.photoediting.ui

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heavy.photoediting.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt
import android.graphics.Canvas as AndroidCanvas

enum class EditorTab(val label: String, val icon: ImageVector) {
    ADJUST("Adjust", Icons.Rounded.Tune), CROP("Crop", Icons.Rounded.Crop),
    FILTERS("Filters", Icons.Rounded.AutoAwesome), BEAUTY("Beauty", Icons.Rounded.Face),
    STICKERS("Stickers", Icons.Rounded.EmojiEmotions), TEXT("Text", Icons.Rounded.TextFields),
    FRAMES("Frames", Icons.Rounded.FilterFrames)
}

private val TextColors = listOf(0xFFFFFFFF, 0xFF111111, 0xFFFF4DA6, 0xFF22D3EE, 0xFFFFD54F, 0xFF7C4DFF, 0xFF69F0AE)
private val Emojis = listOf("😀", "😍", "😎", "🥰", "😂", "🤩", "😜", "😇", "🔥", "✨", "💖", "⭐", "👑", "🌈", "🦋", "🌸",
    "🌟", "💫", "🎀", "🎉", "🎈", "🍀", "🌙", "☀️", "❤️", "💎", "🕶️", "🎧", "👒", "🐱", "🐶", "🦄", "🍓", "🍒", "🌴", "🌊", "⚡", "🎵", "💬", "☁️")

@Composable
fun EditorScreen(uri: Uri, startTab: EditorTab, onClose: () -> Unit, onOpenGallery: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var face by remember { mutableStateOf<FaceData?>(null) }
    var faceChecked by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var st by remember { mutableStateOf(EditState()) }
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var tab by remember { mutableStateOf(startTab) }
    var comparing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var draft by remember { mutableStateOf("") }
    var draftColor by remember { mutableStateOf(TextColors[0]) }

    val stNow by rememberUpdatedState(st)
    val selNow by rememberUpdatedState(selected)

    LaunchedEffect(uri) {
        try {
            val b = withContext(Dispatchers.IO) { Storage.decode(ctx, uri, 1600) }
            src = b
            thumb = Bitmap.createScaledBitmap(b, 160, (160f * b.height / b.width).toInt().coerceAtLeast(1), true)
            face = FaceAnalyzer.detect(b)
            faceChecked = true
        } catch (e: Exception) { failed = true }
    }

    val baseState = st.baseOnly()
    LaunchedEffect(src, face, baseState) {
        val s = src ?: return@LaunchedEffect
        delay(30) // debounce slider drags
        base = withContext(Dispatchers.Default) { Pipeline.renderBase(s, face, baseState) }
    }

    fun addOverlay(text: String, emoji: Boolean, color: Int = android.graphics.Color.WHITE) {
        val o = Overlay(System.nanoTime(), text, color, size = if (emoji) 0.18f else 0.09f, isEmoji = emoji)
        st = st.copy(overlays = st.overlays + o); selected = o.id
    }

    fun export() {
        val s = src ?: return
        scope.launch {
            saving = true
            try {
                savedUri = withContext(Dispatchers.Default) {
                    fun run(maxSide: Int): Uri {
                        val full = Storage.decode(ctx, uri, maxSide)
                        val out = Pipeline.renderBase(full, face, st).copy(Bitmap.Config.ARGB_8888, true)
                        Pipeline.drawOverlays(AndroidCanvas(out), out.width.toFloat(), out.height.toFloat(), st.overlays)
                        return Storage.saveJpeg(ctx, out, 95)
                    }
                    try { run(3840) } catch (e: OutOfMemoryError) { run(2560) }
                }
                InterstitialManager.noteSave()
            } catch (e: Throwable) {
                Toast.makeText(ctx, "Couldn't save photo", Toast.LENGTH_LONG).show()
            }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize().background(HColors.Bg).systemBarsPadding()) {
        // ---- top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = HColors.Text) }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (comparing) HColors.Violet else HColors.Surface2)
                    .pointerInput(Unit) { detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false }) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text(if (comparing) "Original" else "Hold: before / after", color = HColors.Text, fontSize = 13.sp) }
            Spacer(Modifier.weight(1f))
            if (selected != null) IconButton(onClick = {
                st = st.copy(overlays = st.overlays.filter { it.id != selected }); selected = null
            }) { Icon(Icons.Rounded.Delete, null, tint = HColors.Pink) }
            if (saving) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), color = HColors.Cyan, strokeWidth = 2.dp)
            else IconButton(onClick = { export() }, enabled = base != null) { Icon(Icons.Rounded.Check, null, tint = HColors.Cyan) }
        }

        // ---- preview
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            val b = base
            if (b == null) {
                if (failed) Text("Couldn't open this image", color = HColors.Muted)
                else CircularProgressIndicator(color = HColors.Violet)
            } else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ar = b.width.toFloat() / b.height
                val w = minOf(maxWidth, maxHeight * ar)
                val shown = if (comparing) (src ?: b) else b
                val ib = remember(shown) { shown.asImageBitmap() }
                Box(
                    Modifier.size(w, w / ar)
                        .pointerInput(Unit) {
                            detectTapGestures { p ->
                                val sw = size.width.toFloat(); val sh = size.height.toFloat()
                                val hit = stNow.overlays.minByOrNull { hypot(it.cx * sw - p.x, it.cy * sh - p.y) }
                                    ?.takeIf { hypot(it.cx * sw - p.x, it.cy * sh - p.y) < it.size * sw * 1.2f }
                                selected = hit?.id
                            }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, rot ->
                                val id = selNow ?: return@detectTransformGestures
                                val sw = size.width.toFloat(); val sh = size.height.toFloat()
                                st = stNow.copy(overlays = stNow.overlays.map {
                                    if (it.id != id) it else it.copy(
                                        cx = (it.cx + pan.x / sw).coerceIn(0f, 1f), cy = (it.cy + pan.y / sh).coerceIn(0f, 1f),
                                        size = (it.size * zoom).coerceIn(0.03f, 0.8f), rot = it.rot + rot)
                                })
                            }
                        }
                ) {
                    Image(ib, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (!comparing) Canvas(Modifier.fillMaxSize()) {
                        drawIntoCanvas { Pipeline.drawOverlays(it.nativeCanvas, size.width, size.height, st.overlays, selected) }
                    }
                }
            }
        }

        // ---- tool panel
        Box(Modifier.fillMaxWidth().height(190.dp).background(HColors.Surface).padding(12.dp)) {
            when (tab) {
                EditorTab.ADJUST -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    SliderRow("Brightness", st.brightness) { st = st.copy(brightness = it) }
                    SliderRow("Contrast", st.contrast) { st = st.copy(contrast = it) }
                    SliderRow("Saturation", st.saturation) { st = st.copy(saturation = it) }
                    SliderRow("Exposure", st.exposure) { st = st.copy(exposure = it) }
                }
                EditorTab.CROP -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToolButton(Icons.Rounded.RotateLeft, "Rotate L") { st = st.copy(rotation = (st.rotation + 3) % 4) }
                        ToolButton(Icons.Rounded.RotateRight, "Rotate R") { st = st.copy(rotation = (st.rotation + 1) % 4) }
                        ToolButton(Icons.Rounded.Flip, "Flip") { st = st.copy(flipH = !st.flipH) }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CropAspect.values().toList()) { a -> Pill(a.label, st.aspect == a) { st = st.copy(aspect = a) } }
                    }
                }
                EditorTab.FILTERS -> Column {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(Filters.all) { i, f ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { st = st.copy(filter = i) }) {
                                thumb?.let {
                                    Image(it.asImageBitmap(), null,
                                        Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                                            .border(BorderStroke(if (st.filter == i) 2.dp else 0.dp, HColors.Cyan), RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop,
                                        colorFilter = if (i == 0) null else ColorFilter.colorMatrix(ColorMatrix(f.matrix().array)))
                                }
                                Text(f.name, color = if (st.filter == i) HColors.Cyan else HColors.Muted, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                    SliderRow("Intensity", (st.filterStrength * 100).roundToInt(), 0..100) { st = st.copy(filterStrength = it / 100f) }
                }
                EditorTab.BEAUTY -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(when {
                        !faceChecked -> "Detecting face…"
                        face == null -> "No face found - skin, eyes, slim and teeth need a visible face."
                        else -> "Face detected ✓"
                    }, color = if (face != null) HColors.Cyan else HColors.Muted, fontSize = 12.sp)
                    SliderRow("Skin smooth", st.smooth, 0..100) { st = st.copy(smooth = it) }
                    SliderRow("Skin glow", st.glow, 0..100) { st = st.copy(glow = it) }
                    SliderRow("Face slim", st.slim, 0..100) { st = st.copy(slim = it) }
                    SliderRow("Big eyes", st.eyes, 0..100) { st = st.copy(eyes = it) }
                    SliderRow("Whiten teeth", st.teeth, 0..100) { st = st.copy(teeth = it) }
                    SliderRow("Lipstick", st.lip, 0..100) { st = st.copy(lip = it) }
                    SliderRow("Blush", st.blush, 0..100) { st = st.copy(blush = it) }
                }
                EditorTab.STICKERS -> LazyVerticalGrid(GridCells.Adaptive(46.dp)) {
                    items(Emojis) { e ->
                        Box(Modifier.size(46.dp).clickable { addOverlay(e, true) }, contentAlignment = Alignment.Center) {
                            Text(e, fontSize = 28.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                EditorTab.TEXT -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), singleLine = true,
                            placeholder = { Text("Type something…") })
                        Button(onClick = { if (draft.isNotBlank()) { addOverlay(draft.trim(), false, draftColor.toInt()); draft = "" } }) { Text("Add") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextColors.forEach { c ->
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .border(BorderStroke(if (draftColor == c) 3.dp else 1.dp, if (draftColor == c) HColors.Cyan else HColors.Muted), CircleShape)
                                .clickable {
                                    draftColor = c
                                    val id = selected
                                    if (id != null) st = st.copy(overlays = st.overlays.map { if (it.id == id && !it.isEmoji) it.copy(color = c.toInt()) else it })
                                })
                        }
                    }
                    Text("Drag, pinch and twist on the photo to move, resize and rotate.", color = HColors.Muted, fontSize = 12.sp)
                }
                EditorTab.FRAMES -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FrameStyle.values().toList()) { f -> Pill(f.label, st.frame == f) { st = st.copy(frame = f) } }
                }
            }
        }

        // ---- tabs
        Row(Modifier.fillMaxWidth().background(HColors.Bg).horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            EditorTab.values().forEach { t ->
                Column(
                    Modifier.clickable { tab = t }.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(t.icon, null, tint = if (tab == t) HColors.Cyan else HColors.Muted)
                    Text(t.label, fontSize = 11.sp, color = if (tab == t) HColors.Cyan else HColors.Muted)
                }
            }
        }
    }

    savedUri?.let { out ->
        AlertDialog(
            onDismissRequest = { savedUri = null }, containerColor = HColors.Surface,
            title = { Text("Saved ✓", color = HColors.Text) },
            text = { Text("Saved to Pictures/HeavyPhotoEditing in full quality.", color = HColors.Muted) },
            confirmButton = { TextButton(onClick = { Storage.share(ctx, out) }) { Text("Share") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { savedUri = null; (ctx as? Activity)?.let { InterstitialManager.showIfDue(it) } }) { Text("Keep editing") }
                    TextButton(onClick = { savedUri = null; (ctx as? Activity)?.let { InterstitialManager.showIfDue(it) }; onOpenGallery() }) { Text("My Photos") }
                }
            }
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange = -100..100, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp), color = HColors.Muted, fontSize = 13.sp)
        Slider(
            value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(), modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = HColors.Violet, activeTrackColor = HColors.Violet)
        )
        Text("$value", Modifier.width(36.dp), color = HColors.Text, fontSize = 13.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(HColors.Surface2).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = HColors.Cyan)
        Text(label, color = HColors.Text, fontSize = 11.sp)
    }
}
