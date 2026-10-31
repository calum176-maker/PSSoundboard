package com.jms.pssoundboard.ui.theme

import androidx.compose.ui.graphics.Color

val SlateBackground = Color(0xFF0F172A)
val SlateSurface = Color(0xFF1E293B)
val SlateSurfaceHigh = Color(0xFF273449)
val SlateOutline = Color(0xFF334155)
val SlateText = Color(0xFFE2E8F0)
val SlateTextMuted = Color(0xFF94A3B8)

val Teal = Color(0xFF008080)
val TealBright = Color(0xFF14B8A6)
val PanicRed = Color(0xFFEF4444)
val ReadyGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)

/** Preset pad colors offered in the pad editor. */
val PadPalette = listOf(
    "#008080", "#14B8A6", "#22C55E", "#3B82F6", "#6366F1", "#A855F7",
    "#EC4899", "#EF4444", "#F97316", "#F59E0B", "#EAB308", "#334155",
)

fun String.toComposeColor(fallback: Color = SlateOutline): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(fallback)
