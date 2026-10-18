package com.doppio.syncdo.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Semantic status indicators, no direct M3 color role equivalent
object SyncDoStatusColors {
    val Synced = Mo.StatusPositive
    val Syncing = Mo.StatusWarning
    val Offline = Mo.TextTertiary
    val Error = Mo.ErrorFg
}

// Every M3 role points at a Mouture token
private val SyncDoDarkColorScheme = darkColorScheme(
    primary = Mo.Accent,
    onPrimary = Color.White,
    primaryContainer = Mo.AccentDim,
    onPrimaryContainer = Mo.TextPrimary,

    secondary = Mo.AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Mo.AccentBlueDim,
    onSecondaryContainer = Mo.TextPrimary,

    tertiary = Mo.AccentPink,
    onTertiary = Color.White,
    tertiaryContainer = Mo.AccentPinkDim,
    onTertiaryContainer = Mo.TextPrimary,

    background = Mo.BgPage,
    onBackground = Mo.TextPrimary,
    surface = Mo.BgPage,
    onSurface = Mo.TextPrimary,

    surfaceVariant = Mo.BgControl,
    onSurfaceVariant = Mo.TextSecondary,
    surfaceContainerLowest = Mo.BgMuted,
    surfaceContainerLow = Mo.BgCard,
    surfaceContainer = Mo.BgControl,
    surfaceContainerHigh = Mo.BorderPrimary,
    surfaceContainerHighest = Mo.BorderPrimary,

    outline = Mo.TextTertiary,
    outlineVariant = Mo.BorderPrimary,

    error = Mo.ErrorFg,
    onError = Mo.ErrorDim,
    errorContainer = Mo.ErrorDim,
    onErrorContainer = Mo.ErrorOnDim,

    inverseSurface = Mo.TextPrimary,
    inverseOnSurface = Mo.BgCard,
    inversePrimary = Mo.BrandGradientStart,

    scrim = Mo.ShadowInk,
    surfaceTint = Mo.Accent,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncDoTheme(content: @Composable () -> Unit) {
    val typography = rememberSyncDoTypography()
    val mono = rememberMonoStyles()
    CompositionLocalProvider(LocalMonoStyles provides mono) {
        MaterialExpressiveTheme(
            colorScheme = SyncDoDarkColorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography,
            content = content
        )
    }
}
