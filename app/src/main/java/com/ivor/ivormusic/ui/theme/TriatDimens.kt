package com.ivor.ivormusic.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.ui.adaptive.WindowLayout
import com.ivor.ivormusic.ui.adaptive.WindowWidth

/**
 * Spacing and shape tokens as Compose values. The numbers come from
 * [TriatTokens] (generated from `design/tokens.json`), so a token change reaches
 * every screen that reads these rather than a literal.
 *
 * Shapes are deliberately few: a small radius for small artwork, a larger one
 * for large artwork and panels, a sheet radius, and the pill. The pill is the
 * one "active" marker, so it is never used for a resting container.
 */
object TriatDimens {
    val s1: Dp = TriatTokens.s1Dp.dp
    val s2: Dp = TriatTokens.s2Dp.dp
    val s3: Dp = TriatTokens.s3Dp.dp
    val s4: Dp = TriatTokens.s4Dp.dp
    val s5: Dp = TriatTokens.s5Dp.dp
    val s6: Dp = TriatTokens.s6Dp.dp
    val s7: Dp = TriatTokens.s7Dp.dp
    val s8: Dp = TriatTokens.s8Dp.dp

    /** Smallest touch target (WCAG 2.5.8 and Android's own 48dp rule). */
    val touchTarget: Dp = 48.dp

    /** Height of the mini player bar. */
    val miniPlayerHeight: Dp = 64.dp

    /** Artwork in the mini player and list rows. */
    val artRow: Dp = s7

    val artSmall = RoundedCornerShape(TriatTokens.rArtSDp.dp)
    val artLarge = RoundedCornerShape(TriatTokens.rArtLDp.dp)
    val panel = RoundedCornerShape(TriatTokens.rPanelDp.dp)
    val sheet = RoundedCornerShape(topStart = TriatTokens.rSheetDp.dp, topEnd = TriatTokens.rSheetDp.dp)
    val pill = CircleShape

    /**
     * Side margin of screen content: 20dp on a phone, 24 on a small tablet or
     * an unfolded foldable, 32 beyond that.
     */
    fun contentMargin(layout: WindowLayout): Dp = when (layout.width) {
        WindowWidth.COMPACT -> 20.dp
        WindowWidth.MEDIUM -> s5
        WindowWidth.EXPANDED -> s6
    }
}

/**
 * Material shapes resolved from the tokens: 6 (small art), 8 (panels), 12
 * (large art), 16, 28 (sheets). Replaces the generic 4/8/12/16/24 set.
 */
internal val TriatShapes = Shapes(
    extraSmall = RoundedCornerShape(TriatTokens.rArtSDp.dp),
    small = RoundedCornerShape(TriatTokens.rPanelDp.dp),
    medium = RoundedCornerShape(TriatTokens.rArtLDp.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(TriatTokens.rSheetDp.dp),
)
