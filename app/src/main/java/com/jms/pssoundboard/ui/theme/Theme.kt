package com.jms.pssoundboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val SoundboardColorScheme = darkColorScheme(
    primary = SpewYellow,
    onPrimary = InkOnBlob,
    primaryContainer = Color(0xFF4A3410),
    onPrimaryContainer = Color(0xFFFFE2A8),
    secondary = TealBright,
    onSecondary = Ground,
    tertiary = CableAmber,
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

/** App colours with no Material 3 slot. Read them through [MaterialTheme.spew]. */
@Immutable
data class SpewColors(
    /** Purple accent for focus borders, progress and secondary actions. */
    val accent: Color,
    val onAccent: Color,
    /** Text-field and input background. */
    val field: Color,
    /** Text and icons on top of a bright blob. */
    val onBlob: Color,
    /** Text and icons on the STOP ALL button. */
    val onStop: Color,
    val ready: Color,
    val warning: Color,
    val disabled: Color,
)

private val SoundboardSpewColors = SpewColors(
    accent = Teal,
    onAccent = Color.White,
    field = FieldBackground,
    onBlob = InkOnBlob,
    onStop = StopInk,
    ready = ReadyGreen,
    warning = WarningAmber,
    disabled = DisabledInk,
)

private val LocalSpewColors = staticCompositionLocalOf { SoundboardSpewColors }

val MaterialTheme.spew: SpewColors
    @Composable @ReadOnlyComposable get() = LocalSpewColors.current

/** Always-dark Material 3 theme tuned for a dim gaming setup. */
@Composable
fun PSSoundboardTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpewColors provides SoundboardSpewColors) {
        MaterialTheme(
            colorScheme = SoundboardColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
