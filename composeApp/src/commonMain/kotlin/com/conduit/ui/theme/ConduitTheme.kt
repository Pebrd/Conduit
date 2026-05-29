package com.conduit.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import nox.designsystem.NoxTokens

// ── CONDUIT PREMIUM DESIGN SYSTEM ──
// Auto-bound to Nox Design System (github.com/pebrd/nox)

val AmoledBlack    = NoxTokens.ColorBgBase          // AMOLED Black base
val SurfaceDark    = NoxTokens.ColorBgSurface       // Nox primary surface
val SurfaceVariant = NoxTokens.ColorBgSurface2      // Nox secondary surface
val DividerColor   = NoxTokens.ColorBgBorder        // Nox border/divider color
val AccentSage     = NoxTokens.ColorAccentDefault   // Steel default accent
val AccentBlue     = NoxTokens.ColorAccentBlue      // Tidal blue weather accent
val OnSurface      = NoxTokens.ColorTextPrimary     // Full white
val OnSurfaceDim   = NoxTokens.ColorTextSecondary   // Muted gray
val ErrorRed       = NoxTokens.ColorSemanticDanger
val WarningYellow  = NoxTokens.ColorAccentYellow
val SuccessGreen   = NoxTokens.ColorAccentDefault   // Steel default accent
val AccentPurple   = NoxTokens.ColorAccentPurple    // Cool violet accent

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

// Sharp brutalist borders as specified by NoxTokens
private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(NoxTokens.BorderRadiusNone),
    small      = RoundedCornerShape(NoxTokens.BorderRadiusNone),
    medium     = RoundedCornerShape(NoxTokens.BorderRadiusNone),
    large      = RoundedCornerShape(NoxTokens.BorderRadiusNone),
    extraLarge = RoundedCornerShape(NoxTokens.BorderRadiusNone),
)

@Composable
fun ConduitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        shapes      = Shapes,
        typography  = ConduitTypography,
        content     = content,
    )
}

