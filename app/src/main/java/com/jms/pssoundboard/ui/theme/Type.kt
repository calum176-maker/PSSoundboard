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

val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = BricolageCondensed, fontWeight = FontWeight.ExtraBold),
    displayMedium = Base.displayMedium.copy(fontFamily = BricolageCondensed, fontWeight = FontWeight.ExtraBold),
    displaySmall = Base.displaySmall.copy(fontFamily = BricolageCondensed, fontWeight = FontWeight.ExtraBold),
    headlineLarge = Base.headlineLarge.copy(fontFamily = BricolageCondensed, fontWeight = FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = BricolageCondensed, fontWeight = FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = BricolageCondensed, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(
        fontFamily = BricolageCondensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = Base.titleMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Bricolage),
    bodySmall = Base.bodySmall.copy(fontFamily = Bricolage),
    labelLarge = Base.labelLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    labelMedium = Base.labelMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)
