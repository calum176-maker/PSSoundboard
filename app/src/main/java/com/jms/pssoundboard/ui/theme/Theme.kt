package com.jms.pssoundboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.jms.pssoundboard.data.ColorTheme
import com.jms.pssoundboard.data.ThemeMode
import com.jms.pssoundboard.data.ThemePreferences

fun buildSoundboardColorScheme(
    themeMode: ThemeMode,
    colorTheme: ColorTheme,
    systemInDark: Boolean
): ColorScheme {
    val effectiveDark = when (themeMode) {
        ThemeMode.AUTO -> systemInDark
        ThemeMode.DARK, ThemeMode.NIGHT -> true
        ThemeMode.LIGHT -> false
    }

    val primary = colorTheme.primary
    val onPrimary = colorTheme.onPrimary
    val accent = colorTheme.accent

    return if (!effectiveDark) {
        // Light mode
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.2f),
            onPrimaryContainer = Color(0xFF1E293B),
            secondary = accent,
            onSecondary = Color.White,
            tertiary = CableAmber,
            error = StopRed,
            onError = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF64748B),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF8FAFC),
            surfaceContainer = Color(0xFFF1F5F9),
            surfaceContainerHigh = Color(0xFFE2E8F0),
            surfaceContainerHighest = Color(0xFFCBD5E1),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0),
        )
    } else if (themeMode == ThemeMode.NIGHT) {
        // Night / OLED pitch-black mode
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.25f),
            onPrimaryContainer = Color(0xFFFFE2A8),
            secondary = accent,
            onSecondary = Color.Black,
            tertiary = CableAmber,
            error = StopRed,
            onError = Color.White,
            background = Color.Black,
            onBackground = Color(0xFFF8FAFC),
            surface = Color.Black,
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF0C0C12),
            onSurfaceVariant = Color(0xFF94A3B8),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0E),
            surfaceContainer = Color(0xFF101016),
            surfaceContainerHigh = Color(0xFF181822),
            surfaceContainerHighest = Color(0xFF22222E),
            outline = Color(0xFF2A2A38),
            outlineVariant = Color(0xFF2A2A38),
        )
    } else {
        // Dark gaming setup mode (default)
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.25f),
            onPrimaryContainer = Color(0xFFFFE2A8),
            secondary = accent,
            onSecondary = Ground,
            tertiary = CableAmber,
            error = StopRed,
            onError = Color.White,
            background = Ground,
            onBackground = InkLight,
            surface = Ground,
            onSurface = InkLight,
            surfaceVariant = Surface1,
            onSurfaceVariant = InkMuted,
            surfaceContainerLowest = Ground,
            surfaceContainerLow = Surface1,
            surfaceContainer = Surface1,
            surfaceContainerHigh = Surface2,
            surfaceContainerHighest = Surface2,
            outline = SurfaceOutline,
            outlineVariant = SurfaceOutline,
        )
    }
}

fun buildSpewColors(
    themeMode: ThemeMode,
    colorTheme: ColorTheme,
    systemInDark: Boolean
): SpewColors {
    val effectiveDark = when (themeMode) {
        ThemeMode.AUTO -> systemInDark
        ThemeMode.DARK, ThemeMode.NIGHT -> true
        ThemeMode.LIGHT -> false
    }

    val fieldBg = when {
        !effectiveDark -> Color(0xFFE2E8F0)
        themeMode == ThemeMode.NIGHT -> Color(0xFF121218)
        else -> FieldBackground
    }

    return SpewColors(
        accent = colorTheme.accent,
        onAccent = Color.White,
        field = fieldBg,
        onBlob = colorTheme.onPrimary,
        onStop = StopInk,
        ready = ReadyGreen,
        warning = WarningAmber,
        disabled = if (!effectiveDark) Color(0xFF94A3B8) else DisabledInk,
    )
}

/** App colours with no Material 3 slot. Read them through [MaterialTheme.spew]. */
@Immutable
data class SpewColors(
    val accent: Color,
    val onAccent: Color,
    val field: Color,
    val onBlob: Color,
    val onStop: Color,
    val ready: Color,
    val warning: Color,
    val disabled: Color,
)

private val DefaultSpewColors = SpewColors(
    accent = AccentPurple,
    onAccent = Color.White,
    field = FieldBackground,
    onBlob = InkOnBlob,
    onStop = StopInk,
    ready = ReadyGreen,
    warning = WarningAmber,
    disabled = DisabledInk,
)

private val LocalSpewColors = staticCompositionLocalOf { DefaultSpewColors }

val MaterialTheme.spew: SpewColors
    @Composable @ReadOnlyComposable get() = LocalSpewColors.current

@Composable
fun PSSoundboardTheme(
    themeMode: ThemeMode? = null,
    colorTheme: ColorTheme? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()
    val preferences = ThemePreferences.get(context)
    val currentThemeMode by preferences.themeMode.collectAsState()
    val currentColorTheme by preferences.colorTheme.collectAsState()
    val resolvedMode = themeMode ?: currentThemeMode
    val resolvedColor = colorTheme ?: currentColorTheme

    val colorScheme = buildSoundboardColorScheme(resolvedMode, resolvedColor, systemInDark)
    val spewColors = buildSpewColors(resolvedMode, resolvedColor, systemInDark)

    CompositionLocalProvider(LocalSpewColors provides spewColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
