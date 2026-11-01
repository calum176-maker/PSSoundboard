package com.jms.pssoundboard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jms.pssoundboard.R

// Bricolage Grotesque is a variable font (weight, width, optical size); licence in assets/licenses.
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, width: Float = 100f) = Font(
    R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
    ),
)

/** Body and UI text. */
val Bricolage = FontFamily(
    bricolage(400), bricolage(500), bricolage(600), bricolage(700), bricolage(800),
)

/** Squashed display cut for titles and pad labels. */
val BricolageCondensed = FontFamily(
    bricolage(600, 85f), bricolage(700, 85f), bricolage(800, 78f),
)

private val Base = Typography()

private fun condensed(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = BricolageCondensed,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

private fun body(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Bricolage,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

// The app's type scale. Screens pick a style from MaterialTheme.typography instead of setting sizes:
//   displayMedium   splash title              headlineMedium  dialog and screen titles
//   displaySmall    PlayfulHeader title       headlineSmall   smaller screen titles
//   titleLarge      card titles, STOP ALL     titleMedium     section and item titles (condensed)
//   titleSmall      list row titles           bodyLarge       inputs and long text
//   bodyMedium      body text                 bodySmall       hints and secondary text
//   labelLarge      buttons                   labelMedium     chips, eyebrows, small actions
//   labelSmall      tags, counters, nav labels
// Only text that sizes itself to fit (pad labels, the deck title) sets a size directly.
val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = BricolageCondensed, fontWeight = FontWeight.ExtraBold),
    displayMedium = condensed(46, 48),
    displaySmall = condensed(38, 36),
    headlineLarge = Base.headlineLarge.copy(fontFamily = BricolageCondensed, fontWeight = FontWeight.ExtraBold),
    headlineMedium = condensed(28, 32),
    headlineSmall = condensed(24, 28),
    titleLarge = condensed(22, 26),
    titleMedium = condensed(17, 20),
    titleSmall = body(16, 20, FontWeight.Bold),
    bodyLarge = body(16, 24),
    bodyMedium = body(14, 20),
    bodySmall = body(12, 16),
    labelLarge = body(15, 20, FontWeight.Bold),
    labelMedium = body(13, 16, FontWeight.Bold),
    labelSmall = body(11, 14, FontWeight.Bold),
)
