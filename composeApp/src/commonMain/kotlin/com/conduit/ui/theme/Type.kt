package com.conduit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import conduit.composeapp.generated.resources.*
import nox.designsystem.NoxTokens

val IbmPlexSans: FontFamily
    @androidx.compose.runtime.Composable
    get() = FontFamily(
        Font(Res.font.ibm_plex_sans_light, FontWeight.Light),
        Font(Res.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    )

val IbmPlexMono: FontFamily
    @androidx.compose.runtime.Composable
    get() = FontFamily(
        Font(Res.font.ibm_plex_mono_light, FontWeight.Light),
        Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
    )

val JetBrainsMono: FontFamily
    @androidx.compose.runtime.Composable
    get() = IbmPlexMono

val ProtoMono: FontFamily
    @androidx.compose.runtime.Composable
    get() = IbmPlexMono

val ConduitTypography: Typography
    @androidx.compose.runtime.Composable
    get() = Typography(
        // UI general - Usando la fuente Display de Nox (IBM Plex Sans)
        bodyLarge = TextStyle(fontFamily = IbmPlexSans, fontSize = NoxTokens.FontSizeBase, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        bodyMedium = TextStyle(fontFamily = IbmPlexSans, fontSize = NoxTokens.FontSizeSm, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        bodySmall = TextStyle(fontFamily = IbmPlexSans, fontSize = NoxTokens.FontSizeXs, fontWeight = FontWeight.Light, letterSpacing = 0.sp),

        // Títulos - Usando la fuente Display de Nox (IBM Plex Sans)
        titleLarge = TextStyle(fontFamily = IbmPlexSans, fontSize = NoxTokens.FontSizeLg, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = TextStyle(fontFamily = IbmPlexSans, fontSize = NoxTokens.FontSizeMd, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = TextStyle(fontFamily = IbmPlexSans, fontSize = NoxTokens.FontSizeBase, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),

        // Labels y elementos técnicos (IDs, ISRCs, scores, logs) - Usando Mono de Nox (IBM Plex Mono)
        labelLarge = TextStyle(fontFamily = IbmPlexMono, fontSize = NoxTokens.FontSizeSm, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp),
        labelMedium = TextStyle(fontFamily = IbmPlexMono, fontSize = NoxTokens.FontSizeXs, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = IbmPlexMono, fontSize = NoxTokens.FontSizeXs, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
    )

