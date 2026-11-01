package com.jms.pssoundboard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Screens and dialogs read UI colours from MaterialTheme.colorScheme, or MaterialTheme.spew for the
// app-only ones (Theme.kt). The named constants they may use directly are the fixed decorative
// palettes at the bottom of this file, which never change with the theme.

// ---- Playful palette ----------------------------------------------------------------------

val Ground = Color(0xFF14101C)
val Surface1 = Color(0xFF1E1828)
val Surface2 = Color(0xFF2A2236)
val FieldBackground = Color(0xFF221B2D)
val InkLight = Color(0xFFFBF6FF)
val InkMuted = Color(0xFFCDBFE0)

/** Text and icons on top of a bright blob. */
val InkOnBlob = Color(0xFF1A0F14)
val SpewYellow = Color(0xFFFFA400)
val StopRed = Color(0xFFFF4436)
val CableAmber = Color(0xFFF6C35B)

// ---- Legacy names, remapped to the playful palette until each screen is restyled ----------

val SlateBackground = Ground
val SlateSurface = Surface1
val SlateSurfaceHigh = Surface2
val SlateOutline = Color(0xFF3A3048)
val SlateText = InkLight
val SlateTextMuted = InkMuted

val Teal = Color(0xFF7C3AED)
val TealBright = Color(0xFFC4B5FD)
val PanicRed = StopRed
val ReadyGreen = Color(0xFF3EE06A)
val WarningAmber = Color(0xFFF59E0B)
val StopInk = Color(0xFF1F0604)
val DisabledInk = Color(0xFF8F82A3)

// ---- Fixed decorative palettes -------------------------------------------------------------

/** Pin star colour; matches the mockup's small yellow star. */
val PinStar = Color(0xFFFFE020)

/** Each voice effect gets its own bright gummy colour (the stored preset colours are too dark for blobs). */
val PresetColours = mapOf(
    "natural" to Color(0xFFE6DCF2),
    "chipmunk" to Color(0xFFFFA400),
    "deep" to Color(0xFFA46BFF),
    "demon" to Color(0xFFFF6B1F),
    "robot" to Color(0xFF4A86FF),
    "radio" to Color(0xFF3EE06A),
    "alien" to Color(0xFFFF45D0),
    "cave" to Color(0xFF16D4F5),
    "arnold" to Color(0xFF00D9C0),
    "vader" to Color(0xFFFF3D6E),
    "morgan" to Color(0xFFFFE020),
    "batman" to Color(0xFFFF7A6E),
    "ramsay" to Color(0xFFAEF51A),
    "yoda" to Color(0xFF3EE06A),
)

/** Voice FX control colours, one per effect slider. */
val PitchColour = Color(0xFFFF3D6E)
val RobotColour = Color(0xFF4A86FF)
val EchoColour = Color(0xFF3EE06A)
val DriveColour = Color(0xFFFF6B1F)
val GainColour = Color(0xFFFFE020)
val GateColour = Color(0xFF16D4F5)
val RecordHalo = Color(0xFF2A1620)

/** Clip Creator waveform: the selected trim region behind the bars. */
val TrimHighlight = Color(0xFF004D4D)

/** Preset pad colors offered in the pad editor. */
val PadPalette = listOf(
    "#FFA400", "#00D9C0", "#FF3D6E", "#A46BFF", "#3EE06A", "#FF6B1F",
    "#4A86FF", "#FFE020", "#FF45D0", "#16D4F5", "#AEF51A", "#FF7A6E",
)

/** Older pastel pad colours map to their vivid equivalents, so saved decks get the new look. */
private val LegacyPadColours = mapOf(
    "#008080" to "#00D9C0", "#14B8A6" to "#16D4F5", "#06B6D4" to "#16D4F5",
    "#22C55E" to "#3EE06A", "#3B82F6" to "#4A86FF", "#6366F1" to "#A46BFF",
    "#A855F7" to "#A46BFF", "#EC4899" to "#FF45D0", "#EF4444" to "#FF3D6E",
    "#DC2626" to "#FF3D6E", "#F97316" to "#FF6B1F", "#F59E0B" to "#FFA400",
    "#EAB308" to "#FFE020",
)

fun String.toComposeColor(fallback: Color = SlateOutline): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(fallback)

/** A pad's display colour: legacy pastels upgraded, anything else used as chosen. */
fun padColour(hex: String): Color =
    (LegacyPadColours[hex.uppercase()] ?: hex).toComposeColor(Surface2)

/** Dark ink on light colours, light ink on dark ones, so custom pad colours stay readable. */
fun inkOn(background: Color): Color =
    if (background.luminance() > 0.18f) InkOnBlob else InkLight
