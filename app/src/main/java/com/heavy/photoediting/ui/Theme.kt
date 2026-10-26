package com.heavy.photoediting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object HColors {
    val Bg = Color(0xFF0A0A1F); val Surface = Color(0xFF15153A); val Surface2 = Color(0xFF22225A)
    val Violet = Color(0xFF7C4DFF); val Cyan = Color(0xFF22D3EE); val Pink = Color(0xFFFF4DA6)
    val Text = Color(0xFFF2F2FF); val Muted = Color(0xFF9A9AC8)
}

val BrandBrush = Brush.horizontalGradient(listOf(HColors.Violet, HColors.Pink))
val AccentBrush = Brush.horizontalGradient(listOf(HColors.Cyan, HColors.Violet))

@Composable
fun HeavyTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = HColors.Violet, secondary = HColors.Cyan, tertiary = HColors.Pink,
        background = HColors.Bg, surface = HColors.Surface, onBackground = HColors.Text,
        onSurface = HColors.Text, onPrimary = Color.White
    ), content = content
)

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) HColors.Violet else HColors.Surface2)
            .clickableNoRipple(onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = HColors.Text, fontSize = 13.sp) }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
