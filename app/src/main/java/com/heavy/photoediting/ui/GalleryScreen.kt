package com.heavy.photoediting.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.heavy.photoediting.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.heavy.photoediting.engine.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(onOpen: (Uri) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<Uri>?>(null) }
    LaunchedEffect(Unit) { items = withContext(Dispatchers.IO) { Storage.listSaved(ctx) } }
    Column(Modifier.fillMaxSize().background(HColors.Bg).systemBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, null, tint = HColors.Text) }
            Text("My Photos", color = HColors.Text, style = MaterialTheme.typography.titleLarge)
        }
        val list = items
        Box(Modifier.weight(1f)) { when {
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = HColors.Violet) }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No photos yet.\nShoot or edit one and it will appear here.", color = HColors.Muted)
            }
            else -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.toString() }) { uri -> Thumb(uri) { onOpen(uri) } }
            }
        } }
        BannerAdView(BuildConfig.ADMOB_BANNER_ID)
    }
}

@Composable
private fun Thumb(uri: Uri, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bmp = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.loadThumbnail(uri, Size(300, 300), null) }.getOrNull() }
    }
    Box(Modifier.aspectRatio(1f).background(HColors.Surface).clickable(onClick = onClick)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
