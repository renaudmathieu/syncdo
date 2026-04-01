package com.doppio.syncdo.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Semantic status indicators — no direct M3 color role equivalent
object SyncDoStatusColors {
    val Synced  = Color(0xFF22C55E)
    val Syncing = Color(0xFFF59E0B)
    val Offline = Color(0xFF7070A0)
}

// Custom dark color scheme built around primary #3738CC
private val SyncDoDarkColorScheme = darkColorScheme(
    // Primary — seed color used as-is
    primary                 = Color(0xFF3738CC),
    onPrimary               = Color(0xFFFFFFFF),
    primaryContainer        = Color(0xFF1E1FA3),
    onPrimaryContainer      = Color(0xFFC5C6FF),

    // Secondary — muted periwinkle, same hue family
    secondary               = Color(0xFF8889E0),
    onSecondary             = Color(0xFF12126A),
    secondaryContainer      = Color(0xFF2526A8),
    onSecondaryContainer    = Color(0xFFD8D8FF),

    // Tertiary — rose/pink complement
    tertiary                = Color(0xFFCB96CB),
    onTertiary              = Color(0xFF45084A),
    tertiaryContainer       = Color(0xFF5E2062),
    onTertiaryContainer     = Color(0xFFFBCEFF),

    // Backgrounds
    background              = Color(0xFF0D0D1C),
    onBackground            = Color(0xFFE4E4F6),
    surface                 = Color(0xFF0D0D1C),
    onSurface               = Color(0xFFE4E4F6),

    // Surface containers (graduated dark blues for layering)
    surfaceVariant          = Color(0xFF2E2E58),
    onSurfaceVariant        = Color(0xFFB0B0D0),
    surfaceContainerLowest  = Color(0xFF08080F),
    surfaceContainerLow     = Color(0xFF13132A),
    surfaceContainer        = Color(0xFF191935),
    surfaceContainerHigh    = Color(0xFF20203F),
    surfaceContainerHighest = Color(0xFF2A2A50),

    // Outline
    outline                 = Color(0xFF7070A0),
    outlineVariant          = Color(0xFF3C3C6C),

    // Error
    error                   = Color(0xFFFF8A8A),
    onError                 = Color(0xFF680000),
    errorContainer          = Color(0xFF920000),
    onErrorContainer        = Color(0xFFFFDBDB),

    // Inverse
    inverseSurface          = Color(0xFFE4E4F6),
    inverseOnSurface        = Color(0xFF16163A),
    inversePrimary          = Color(0xFF5556EE),

    scrim                   = Color(0xFF000000),
    surfaceTint             = Color(0xFF3738CC),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncDoTheme(content: @Composable () -> Unit) {
    val typography = rememberSyncDoTypography()
    MaterialExpressiveTheme(
        colorScheme = SyncDoDarkColorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        content = content
    )
}
