package com.doppio.syncdo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import syncdo.composeapp.generated.resources.Res
import syncdo.composeapp.generated.resources.lalezar_regular
import syncdo.composeapp.generated.resources.nunito_variable

@Composable
fun rememberSyncDoTypography(): Typography {
    // Lalezar — display font for titles (single weight)
    val lalezar = FontFamily(Font(Res.font.lalezar_regular))

    // Nunito variable font — body & labels (all weights from one file)
    val nunito = FontFamily(
        Font(Res.font.nunito_variable, weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(Res.font.nunito_variable, weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(Res.font.nunito_variable, weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(Res.font.nunito_variable, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

    return remember(lalezar, nunito) {
        Typography(
            // Display → Lalezar
            displayLarge  = TextStyle(fontFamily = lalezar, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
            displayMedium = TextStyle(fontFamily = lalezar, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
            displaySmall  = TextStyle(fontFamily = lalezar, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
            // Headline → Lalezar
            headlineLarge  = TextStyle(fontFamily = lalezar, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontFamily = lalezar, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
            headlineSmall  = TextStyle(fontFamily = lalezar, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
            // Title → Lalezar
            titleLarge  = TextStyle(fontFamily = lalezar, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = lalezar, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
            titleSmall  = TextStyle(fontFamily = lalezar, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            // Body → Nunito
            bodyLarge  = TextStyle(fontFamily = nunito, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = nunito, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall  = TextStyle(fontFamily = nunito, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
            // Label → Nunito
            labelLarge  = TextStyle(fontFamily = nunito, fontWeight = FontWeight.Medium,  fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontFamily = nunito, fontWeight = FontWeight.Medium,  fontSize = 12.sp, lineHeight = 16.sp),
            labelSmall  = TextStyle(fontFamily = nunito, fontWeight = FontWeight.Medium,  fontSize = 11.sp, lineHeight = 16.sp),
        )
    }
}
