package com.ivor.ivormusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriatColorSchemeTest {
    private fun ratio(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private val dark = buildPaletteColorScheme(findPalette(TRIAT_PALETTE_ID)!!, dark = true)

    @Test fun onSurfaceReadsOnEverySurfaceContainer() {
        val surfaces = mapOf(
            "surface" to dark.surface,
            "lowest" to dark.surfaceContainerLowest,
            "low" to dark.surfaceContainerLow,
            "container" to dark.surfaceContainer,
            "high" to dark.surfaceContainerHigh,
            "highest" to dark.surfaceContainerHighest,
            "variant" to dark.surfaceVariant,
            "bright" to dark.surfaceBright,
        )
        for ((name, color) in surfaces) {
            assertTrue("onSurface on $name", ratio(dark.onSurface, color) >= 4.5f)
            assertTrue("onSurfaceVariant on $name", ratio(dark.onSurfaceVariant, color) >= 4.5f)
        }
    }

    @Test fun outlineMarksComponentBoundariesOnEveryContainer() {
        for (bg in listOf(dark.surface, dark.surfaceContainerLow, dark.surfaceContainer,
            dark.surfaceContainerHigh, dark.surfaceContainerHighest)) {
            assertTrue("outline on $bg", ratio(dark.outline, bg) >= 3f)
        }
    }

    @Test fun accentsKeepTheirLabelsReadable() {
        assertTrue(ratio(dark.onPrimary, dark.primary) >= 4.5f)
        assertTrue(ratio(dark.onSecondary, dark.secondary) >= 4.5f)
        assertTrue(ratio(dark.onTertiary, dark.tertiary) >= 4.5f)
        assertTrue(ratio(dark.onError, dark.error) >= 4.5f)
        assertTrue(ratio(dark.onPrimaryContainer, dark.primaryContainer) >= 4.5f)
        assertTrue(ratio(dark.onTertiaryContainer, dark.tertiaryContainer) >= 4.5f)
        assertTrue(ratio(dark.onErrorContainer, dark.errorContainer) >= 4.5f)
    }

    @Test fun groundIsTrueBlackAndIgnoresPaletteStyleAndAmoled() {
        assertEquals(Color.Black, dark.background)
        assertEquals(Color.Black, dark.surface)
        assertEquals(Color(0xFF000000), dark.scrim)
        for (style in PaletteStyle.entries) {
            assertEquals(dark.toString(), buildPaletteColorScheme(findPalette(TRIAT_PALETTE_ID)!!, true, style).toString())
        }
        assertEquals(dark.toString(), dark.withAmoled(TRIAT_PALETTE_ID, amoled = true).toString())
    }

    @Test fun surfaceRampClimbsStrictly() {
        val ramp = listOf(
            dark.surfaceContainerLowest, dark.surfaceContainerLow, dark.surfaceContainer,
            dark.surfaceContainerHigh, dark.surfaceContainerHighest,
        ).map { it.luminance() }
        assertEquals(ramp.sorted(), ramp)
        assertEquals(ramp.distinct(), ramp)
    }

    @Test fun lightModeFallsBackToTheNothingScheme() {
        val light = buildPaletteColorScheme(findPalette(TRIAT_PALETTE_ID)!!, dark = false)
        assertEquals(buildPaletteColorScheme(findPalette("nothing")!!, dark = false).toString(), light.toString())
        assertNotEquals(dark.toString(), light.toString())
    }
}
