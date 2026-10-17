package com.ivor.ivormusic.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutTest {

    @Test
    fun widthBreakpoints() {
        assertEquals(WindowWidth.COMPACT, windowWidthFor(0f))
        assertEquals(WindowWidth.COMPACT, windowWidthFor(599.9f))
        assertEquals(WindowWidth.MEDIUM, windowWidthFor(600f))
        assertEquals(WindowWidth.MEDIUM, windowWidthFor(839.9f))
        assertEquals(WindowWidth.EXPANDED, windowWidthFor(840f))
    }

    @Test
    fun portraitPhoneChangesNothing() {
        val l = windowLayoutFor(412f, 915f)
        assertEquals(WindowWidth.COMPACT, l.width)
        assertFalse(l.heightCompact)
        assertFalse(l.isWideLandscape)
    }

    @Test
    fun phoneInLandscapeIsShortButNotWide() {
        val l = windowLayoutFor(915f, 412f)
        assertEquals(WindowWidth.EXPANDED, l.width)
        assertTrue(l.heightCompact)
        assertTrue(l.isWideLandscape)
    }

    @Test
    fun tabletLandscapeIsWideLandscapeEvenWhenHeightIsNotCompact() {
        val l = windowLayoutFor(1280f, 800f)
        assertFalse(l.heightCompact)
        assertTrue(l.isWideLandscape)
    }

    @Test
    fun tabletPortraitKeepsTheColumn() {
        assertFalse(windowLayoutFor(800f, 1280f).isWideLandscape)
    }

    @Test
    fun unfoldedFoldableNearlySquareStaysColumnUntilWider() {
        assertFalse(windowLayoutFor(700f, 700f).isWideLandscape)
        assertTrue(windowLayoutFor(700f, 650f).isWideLandscape)
    }

    @Test
    fun narrowLandscapeBelowLargeScreenStaysColumn() {
        assertFalse(windowLayoutFor(580f, 360f).isWideLandscape)
    }
}
