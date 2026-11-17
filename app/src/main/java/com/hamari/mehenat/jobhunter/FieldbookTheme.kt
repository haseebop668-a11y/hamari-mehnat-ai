package com.hamari.mehenat.jobhunter

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object FieldbookPalette {
    val Canvas = Color(0xFFF2EEE5)
    val Paper = Color(0xFFFFFCF6)
    val PaperShade = Color(0xFFEAE4D8)
    val Ink = Color(0xFF202C2A)
    val Forest = Color(0xFF214C43)
    val ForestDeep = Color(0xFF163A34)
    val Rust = Color(0xFFB85135)
    val Muted = Color(0xFF66736F)
    val Rule = Color(0xFFD9D1C4)
    val Amber = Color(0xFFC49A53)
    val Danger = Color(0xFF9E3E33)
    val White = Color(0xFFFFFEFA)
}

@Composable
fun HamariMehnatTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = FieldbookPalette.Forest,
        onPrimary = FieldbookPalette.White,
        secondary = FieldbookPalette.Rust,
        onSecondary = FieldbookPalette.White,
        tertiary = FieldbookPalette.Amber,
        onTertiary = FieldbookPalette.Ink,
        background = FieldbookPalette.Canvas,
        onBackground = FieldbookPalette.Ink,
        surface = FieldbookPalette.Paper,
        onSurface = FieldbookPalette.Ink,
        surfaceVariant = FieldbookPalette.PaperShade,
        onSurfaceVariant = FieldbookPalette.Muted,
        outline = FieldbookPalette.Rule,
        error = FieldbookPalette.Danger,
        onError = FieldbookPalette.White
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(30.dp)
        ),
        content = content
    )
}
