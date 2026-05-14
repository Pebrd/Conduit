package com.conduit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import conduit.composeapp.generated.resources.*

val JetBrainsMono: FontFamily
    @androidx.compose.runtime.Composable
    get() = FontFamily(
        Font(Res.font.jetbrains_mono_light, FontWeight.Light),
        Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(Res.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    )

val ProtoMono: FontFamily
    @androidx.compose.runtime.Composable
    get() = JetBrainsMono

val ConduitTypography: Typography
    @androidx.compose.runtime.Composable
    get() = Typography(
        // UI general
        bodyLarge = TextStyle(fontFamily = ProtoMono, fontSize = 15.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        bodyMedium = TextStyle(fontFamily = ProtoMono, fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        bodySmall = TextStyle(fontFamily = ProtoMono, fontSize = 11.sp, fontWeight = FontWeight.Light, letterSpacing = 0.sp),

        // Títulos
        titleLarge = TextStyle(fontFamily = ProtoMono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = TextStyle(fontFamily = ProtoMono, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = TextStyle(fontFamily = ProtoMono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),

        // Labels y elementos técnicos (IDs, ISRCs, scores, logs)
        labelLarge = TextStyle(fontFamily = ProtoMono, fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp),
        labelMedium = TextStyle(fontFamily = ProtoMono, fontSize = 11.sp, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = ProtoMono, fontSize = 10.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
    )
