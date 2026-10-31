package com.jms.pssoundboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SoundboardColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF004D4D),
    onPrimaryContainer = Color(0xFFB2F5EA),
    secondary = TealBright,
    onSecondary = Color(0xFF00201F),
    tertiary = WarningAmber,
    error = PanicRed,
    onError = Color.White,
    background = SlateBackground,
    onBackground = SlateText,
    surface = SlateBackground,
    onSurface = SlateText,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = SlateTextMuted,
    surfaceContainerLowest = SlateBackground,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurface,
    surfaceContainerHigh = SlateSurfaceHigh,
    surfaceContainerHighest = SlateSurfaceHigh,
    outline = SlateOutline,
    outlineVariant = SlateOutline,
)

/** Always-dark Material 3 theme tuned for a dim gaming setup. */
@Composable
fun PSSoundboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoundboardColorScheme,
        typography = Typography,
        content = content,
    )
}
