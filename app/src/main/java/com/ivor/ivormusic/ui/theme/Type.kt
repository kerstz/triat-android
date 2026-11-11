@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.ivor.ivormusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R

// Fonts are bundled (res/font, SIL OFL 1.1): no network fetch, no Play services dependency.

private fun geist(weight: Int) = Font(
    resId = R.font.geist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private fun doto(weight: Int) = Font(
    resId = R.font.doto,
    weight = FontWeight(weight),
    // ROND axis stays at its default (0, square dots).
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Geist: the text face of the whole app (all 15 Material roles). */
val GeistFamily = FontFamily(geist(400), geist(500), geist(600), geist(700))

/**
 * Doto: dot-matrix display face. Only for big numbers and big screen titles through [TriatType].
 * Never for song titles or running text.
 */
val DotoFamily = FontFamily(doto(700), doto(800))

private fun geistStyle(
    weight: FontWeight,
    size: Int,
    line: Int,
    spacingEm: Float = 0f
) = TextStyle(
    fontFamily = GeistFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacingEm.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

val Typography = Typography(
    displayLarge = geistStyle(FontWeight.SemiBold, 44, 48, -0.02f),
    displayMedium = geistStyle(FontWeight.SemiBold, 36, 40, -0.02f),
    displaySmall = geistStyle(FontWeight.SemiBold, 28, 32, -0.01f),
    headlineLarge = geistStyle(FontWeight.SemiBold, 28, 34),
    headlineMedium = geistStyle(FontWeight.SemiBold, 24, 30),
    headlineSmall = geistStyle(FontWeight.SemiBold, 22, 28),
    titleLarge = geistStyle(FontWeight.SemiBold, 22, 28),
    titleMedium = geistStyle(FontWeight.SemiBold, 18, 24),
    titleSmall = geistStyle(FontWeight.SemiBold, 16, 22),
    bodyLarge = geistStyle(FontWeight.Normal, 15, 22),
    bodyMedium = geistStyle(FontWeight.Normal, 14, 20),
    bodySmall = geistStyle(FontWeight.Normal, 12, 16),
    labelLarge = geistStyle(FontWeight.Medium, 14, 20),
    labelMedium = geistStyle(FontWeight.Medium, 12, 16),
    labelSmall = geistStyle(FontWeight.Medium, 11, 16)
)

/**
 * Display styles in Doto, for big numbers (durations, counters, timer) and big screen titles only.
 * Song titles, artist names and running text stay on MaterialTheme.typography (Geist).
 */
object TriatType {
    val displayXl = TextStyle(
        fontFamily = DotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = TriatTokens.displayXlSp.sp,
        lineHeight = TriatTokens.displayXlSp.sp,
        letterSpacing = (-0.02f).em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    val displayL = TextStyle(
        fontFamily = DotoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = TriatTokens.displayLSp.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01f).em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    val displayM = TextStyle(
        fontFamily = DotoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = TriatTokens.displayMSp.sp,
        lineHeight = 26.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    /** Geist with tabular figures, for small times and counters that must not jitter. */
    val numeric = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        fontFeatureSettings = "tnum"
    )
    val numericSmall = numeric.copy(fontSize = TriatTokens.metaSp.sp)
}
