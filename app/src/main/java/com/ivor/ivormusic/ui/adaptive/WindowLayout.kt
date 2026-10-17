package com.ivor.ivormusic.ui.adaptive

import androidx.compose.runtime.staticCompositionLocalOf

/** Smallest width, in dp, from which a display counts as a tablet or unfolded foldable. */
const val LARGE_SCREEN_MIN_DP = 600

/** Width of the window the app is drawn in; the standard Material breakpoints (600, 840). */
enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

private const val EXPANDED_MIN_DP = 840
private const val HEIGHT_COMPACT_MAX_DP = 480

/**
 * The size of the app window, measured in real dp (before the interface-scale
 * override, so a user's scale never changes which layout they get).
 *
 * [heightCompact] is true below 480dp, a phone in landscape. [isWideLandscape]
 * is the condition the side-by-side layouts key on: at least 600dp wide and
 * wider than tall. A tablet in landscape is 800dp tall, which is not "compact"
 * in Material terms yet still too short for a portrait column, hence the
 * separate flag.
 */
data class WindowLayout(
    val width: WindowWidth,
    val heightCompact: Boolean,
    val isWideLandscape: Boolean,
) {
    companion object {
        /** What portrait phones get: every layout stays as it was. */
        val Compact = WindowLayout(WindowWidth.COMPACT, heightCompact = false, isWideLandscape = false)
    }
}

fun windowWidthFor(widthDp: Float): WindowWidth = when {
    widthDp >= EXPANDED_MIN_DP -> WindowWidth.EXPANDED
    widthDp >= LARGE_SCREEN_MIN_DP -> WindowWidth.MEDIUM
    else -> WindowWidth.COMPACT
}

fun windowLayoutFor(widthDp: Float, heightDp: Float): WindowLayout = WindowLayout(
    width = windowWidthFor(widthDp),
    heightCompact = heightDp < HEIGHT_COMPACT_MAX_DP,
    isWideLandscape = widthDp >= LARGE_SCREEN_MIN_DP && widthDp > heightDp,
)

/**
 * Provided once in MainActivity, outside the theme. A phone in portrait reads
 * [WindowLayout.Compact] semantics; nothing may change for it.
 */
val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.Compact }
