package com.ivor.ivormusic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Stored id of the Triat palette. New id: "nothing" keeps its own, persisted ids are frozen. */
const val TRIAT_PALETTE_ID = "triat"

/**
 * The Triat dark scheme, built by hand from [TriatTokens] with no HCT and no
 * [PaletteStyle]: true black ground, white ink, one red signal.
 *
 * Veils are translucent white in the tokens; Material roles need opaque colors,
 * so each is flattened over [TriatTokens.ground] with `compositeOver`.
 * `surfaceContainerLowest` is the ground itself (nothing sits below it), then
 * `Low..Highest` climb veil-1..veil-4. Text uses `ink` and `ink2` only
 * (`ink3` is for meta labels and is not mapped to a Material role).
 *
 * Light mode is not defined by the tokens yet (only a partial set), so the
 * "triat" palette falls back to the HCT scheme of the "nothing" palette there;
 * see [buildPaletteColorScheme].
 */
internal fun triatDarkColorScheme(): ColorScheme {
    val t = TriatTokens
    val ground = t.ground
    fun flat(c: Color) = c.compositeOver(ground)

    val veil1 = flat(t.veil1)
    val veil2 = flat(t.veil2)
    val veil3 = flat(t.veil3)
    val veil4 = flat(t.veil4)
    val signalWash = flat(t.signal.copy(alpha = t.veil4.alpha))

    return darkColorScheme(
        primary = t.ink,
        onPrimary = ground,
        primaryContainer = veil4,
        onPrimaryContainer = t.ink,
        inversePrimary = t.signal,
        secondary = t.ink2,
        onSecondary = ground,
        secondaryContainer = veil3,
        onSecondaryContainer = t.ink,
        tertiary = t.signal,
        onTertiary = t.inkMax,
        tertiaryContainer = signalWash,
        onTertiaryContainer = t.signalInk,
        background = ground,
        onBackground = t.ink,
        surface = ground,
        onSurface = t.ink,
        surfaceVariant = veil2,
        onSurfaceVariant = t.ink2,
        surfaceTint = ground,
        inverseSurface = t.ink,
        inverseOnSurface = ground,
        error = t.danger,
        onError = ground,
        errorContainer = flat(t.dangerSoft),
        onErrorContainer = t.danger,
        outline = veil4,
        outlineVariant = flat(t.hair),
        scrim = t.scrim.copy(alpha = 1f),
        surfaceBright = veil3,
        surfaceDim = ground,
        surfaceContainerLowest = ground,
        surfaceContainerLow = veil1,
        surfaceContainer = veil2,
        surfaceContainerHigh = veil3,
        surfaceContainerHighest = veil4,
        primaryFixed = t.ink,
        primaryFixedDim = t.ink2,
        onPrimaryFixed = ground,
        onPrimaryFixedVariant = veil4,
        secondaryFixed = t.ink2,
        secondaryFixedDim = t.ink3,
        onSecondaryFixed = ground,
        onSecondaryFixedVariant = veil4,
        tertiaryFixed = t.signalInk,
        tertiaryFixedDim = t.signal,
        onTertiaryFixed = ground,
        onTertiaryFixedVariant = ground,
    )
}
