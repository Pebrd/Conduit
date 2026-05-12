package com.spotitidal.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily

// Tipografía
val Inter = FontFamily.SansSerif
val ProtoMono = FontFamily.Monospace

val SpotiTidalTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ProtoMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
)

// ── Colores AMOLED ────────────────────────────────────────────────────────────
val AmoledBlack    = Color(0xFF000000)
val SurfaceDark    = Color(0xFF0A0A0A)
val SurfaceVariant = Color(0xFF111111)
val DividerColor   = Color(0xFF1A1A1A)
val AccentSage     = Color(0xFF9CAF88)   // Muted Sage color
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
    primary          = AccentSage,
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
        typography  = SpotiTidalTypography,
        content     = content,
    )
}
