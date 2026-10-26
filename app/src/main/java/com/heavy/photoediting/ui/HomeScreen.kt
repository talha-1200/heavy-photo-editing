package com.heavy.photoediting.ui

import androidx.compose.foundation.background
import com.heavy.photoediting.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(onCamera: () -> Unit, onPick: (EditorTab) -> Unit, onGallery: () -> Unit) {
    Column(Modifier.fillMaxSize().background(HColors.Bg).systemBarsPadding()) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("Heavy", color = HColors.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("Photo Editing", style = TextStyle(brush = BrandBrush, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold))
            Text("Capture · Edit · Beautify · Share", color = HColors.Muted, fontSize = 13.sp)
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(HColors.Violet, Color2, HColors.Pink))).padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Capture\nBeautiful\nMoments", color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                Button(
                    onClick = onCamera,
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = .2f))
                ) { Text("Open Camera  →", color = androidx.compose.ui.graphics.Color.White) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Camera", Icons.Rounded.CameraAlt, Modifier.weight(1f), onCamera)
            Tile("Photo Editor", Icons.Rounded.Tune, Modifier.weight(1f)) { onPick(EditorTab.ADJUST) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Filters", Icons.Rounded.AutoAwesome, Modifier.weight(1f)) { onPick(EditorTab.FILTERS) }
            Tile("Beauty", Icons.Rounded.Face, Modifier.weight(1f)) { onPick(EditorTab.BEAUTY) }
        }
        Tile("My Photos", Icons.Rounded.Collections, Modifier.fillMaxWidth(), onGallery)
        Text("Free · works offline · no account needed", color = HColors.Muted, fontSize = 12.sp)
    }
    BannerAdView(BuildConfig.ADMOB_BANNER_ID)
    }
}

private val Color2 = androidx.compose.ui.graphics.Color(0xFF5B3FD6)

@Composable
private fun Tile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(HColors.Surface).clickable(onClick = onClick).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = HColors.Cyan, modifier = Modifier.size(30.dp))
        Text(label, color = HColors.Text, fontSize = 14.sp)
    }
}
