package com.doppio.syncdo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import syncdo.composeapp.generated.resources.Res
import syncdo.composeapp.generated.resources.jetbrains_mono_variable
import syncdo.composeapp.generated.resources.lalezar_regular
import syncdo.composeapp.generated.resources.space_grotesk_variable

/** JetBrains Mono styles for overlines and section labels (outside the M3 type scale). */
@Immutable
data class MonoStyles(
    val overline: TextStyle,
    val caption: TextStyle,
)

val LocalMonoStyles = staticCompositionLocalOf<MonoStyles> { error("SyncDoTheme not applied") }

@Composable
private fun variableFamily(resource: org.jetbrains.compose.resources.FontResource): FontFamily =
    FontFamily(
        listOf(400, 500, 600, 700).map { w ->
            Font(
                resource,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w)),
            )
        }
    )

@Composable
internal fun rememberMonoStyles(): MonoStyles {
    val mono = variableFamily(Res.font.jetbrains_mono_variable)
    return remember(mono) {
        MonoStyles(
            overline = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 2.sp),
            caption = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
        )
    }
}

@Composable
fun rememberSyncDoTypography(): Typography {
    // Lalezar: display, headline and titleLarge (single weight)
    val lalezar = FontFamily(Font(Res.font.lalezar_regular))
    // Space Grotesk variable font: titleMedium down, body and labels
    val grotesk = variableFamily(Res.font.space_grotesk_variable)

    return remember(lalezar, grotesk) {
        Typography(
            displayLarge = TextStyle(fontFamily = lalezar, fontSize = 57.sp, lineHeight = 64.sp),
            displayMedium = TextStyle(fontFamily = lalezar, fontSize = 45.sp, lineHeight = 52.sp),
            displaySmall = TextStyle(fontFamily = lalezar, fontSize = 40.sp, lineHeight = 42.sp),
            headlineLarge = TextStyle(fontFamily = lalezar, fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontFamily = lalezar, fontSize = 28.sp, lineHeight = 36.sp),
            headlineSmall = TextStyle(fontFamily = lalezar, fontSize = 24.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontFamily = lalezar, fontSize = 22.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
            titleSmall = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
            bodyLarge = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
            bodyMedium = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
            bodySmall = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
            labelLarge = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
            labelMedium = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
            labelSmall = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
        )
    }
}
