package com.jms.pssoundboard.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.jms.pssoundboard.ui.theme.AccentPurple
import com.jms.pssoundboard.ui.theme.InkOnBlob
import com.jms.pssoundboard.ui.theme.SpewYellow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Night, Dark, Auto (and Light) appearance settings.
 */
enum class ThemeMode(val label: String, val description: String) {
    AUTO("Auto", "Follows device system theme"),
    DARK("Dark", "Dim gaming setup theme"),
    NIGHT("Night", "Pitch black / OLED true black"),
    LIGHT("Light", "Clean bright theme")
}

/**
 * Color themes (accent palettes) customizable across the app.
 */
enum class ColorTheme(
    val label: String,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val previewHex: String
) {
    CYBER_SPEW("Cyber Spew", SpewYellow, InkOnBlob, AccentPurple, "#FFA400"),
    ELECTRIC_VIOLET("Electric Violet", Color(0xFFA855F7), Color.White, Color(0xFFC084FC), "#A855F7"),
    HYPER_CYAN("Hyper Cyan", Color(0xFF06B6D4), Color(0xFF082F49), Color(0xFF38BDF8), "#06B6D4"),
    TOXIC_LIME("Toxic Lime", Color(0xFF22C55E), Color(0xFF052E16), Color(0xFF4ADE80), "#22C55E"),
    HOT_PINK("Hot Pink", Color(0xFFFF3D6E), Color.White, Color(0xFFFB7185), "#FF3D6E"),
    SOLAR_FLAME("Solar Flame", Color(0xFFF97316), Color(0xFF431407), Color(0xFFFB923C), "#F97316"),
    ICE_BLUE("Ice Blue", Color(0xFF3B82F6), Color.White, Color(0xFF60A5FA), "#3B82F6")
}

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("spewboard_theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(loadColorTheme())
    val colorTheme: StateFlow<ColorTheme> = _colorTheme.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.DARK
        return runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.DARK)
    }

    private fun loadColorTheme(): ColorTheme {
        val saved = prefs.getString(KEY_COLOR_THEME, null) ?: return ColorTheme.CYBER_SPEW
        return runCatching { ColorTheme.valueOf(saved) }.getOrDefault(ColorTheme.CYBER_SPEW)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setColorTheme(theme: ColorTheme) {
        _colorTheme.value = theme
        prefs.edit().putString(KEY_COLOR_THEME, theme.name).apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"

        @Volatile
        private var instance: ThemePreferences? = null

        fun get(context: Context): ThemePreferences {
            return instance ?: synchronized(this) {
                instance ?: ThemePreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
