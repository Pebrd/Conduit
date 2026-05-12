package com.spotitidal.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ── Colores AMOLED ────────────────────────────────────────────────────────────
val AmoledBlack    = Color(0xFF000000)
val SurfaceDark    = Color(0xFF0A0A0A)
val SurfaceVariant = Color(0xFF111111)
val DividerColor   = Color(0xFF1A1A1A)
val AccentGreen    = Color(0xFF1DB954)   // Spotify green
val AccentBlue     = Color(0xFF00BFFF)   // Tidal blue
val OnSurface      = Color(0xFFEEEEEE)
val OnSurfaceDim   = Color(0xFF888888)
val ErrorRed       = Color(0xFFFF4444)
val WarningYellow  = Color(0xFFFFBB00)
val SuccessGreen   = Color(0xFF44BB44)

private val ColorScheme = darkColorScheme(
    background       = AmoledBlack,
    surface          = SurfaceDark,
    surfaceVariant   = SurfaceVariant,
    primary          = AccentGreen,
    secondary        = AccentBlue,
    onBackground     = OnSurface,
    onSurface        = OnSurface,
    onSurfaceVariant = OnSurfaceDim,
    error            = ErrorRed,
)


// Sin bordes redondeados
private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small      = RoundedCornerShape(0.dp),
    medium     = RoundedCornerShape(0.dp),
    large      = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun SpotiTidalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        shapes      = Shapes,
        content     = content,
    )
}
