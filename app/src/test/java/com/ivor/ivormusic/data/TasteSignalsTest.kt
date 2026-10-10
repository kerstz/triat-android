package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The skip curve is the only negative signal in the app, so a wrong shape
 * here is a feed that quietly stops offering music the listener would have
 * liked - invisible at a glance and wrong for months.
 */
class TasteSignalsTest {

    private val now = 1_750_000_000_000L
    private fun day(n: Double) = (n * 86_400_000.0).toLong()

    private fun skip(count: Int, daysAgo: Double, artist: String = "Boards of Canada") =
        TasteSignals.Skip("id$count$daysAgo", artist, count, now - day(daysAgo))

    @Test
    fun `nothing skipped is nothing damped`() {
        assertEquals(0.0, skipPenalty(null, now), 0.0)
    }

    @Test
    fun `one fresh skip dampens without suppressing`() {
        val penalty = skipPenalty(skip(count = 1, daysAgo = 0.0), now)
        assertEquals(1.0 / 3.0, penalty, 0.001)
        assertTrue("one skip must leave most of the score", penalty < 0.5)
    }

    @Test
    fun `three skips suppress`() {
        assertEquals(1.0, skipPenalty(skip(count = 3, daysAgo = 0.0), now), 0.001)
    }

    @Test
    fun `more than three cannot exceed total suppression`() {
        assertEquals(1.0, skipPenalty(skip(count = 9, daysAgo = 0.0), now), 0.001)
    }

    @Test
    fun `a rejection halves over the half-life`() {
        val fresh = skipPenalty(skip(count = 3, daysAgo = 0.0), now)
        val aged = skipPenalty(skip(count = 3, daysAgo = SKIP_HALF_LIFE_DAYS), now)
        assertEquals(fresh / 2, aged, 0.001)
    }

    @Test
    fun `an old rejection is nearly forgotten, so taste can change`() {
        val ancient = skipPenalty(skip(count = 3, daysAgo = SKIP_HALF_LIFE_DAYS * 5), now)
        assertTrue("five half-lives must leave almost nothing: $ancient", ancient < 0.05)
    }

    @Test
    fun `a skip in the future cannot read as negative age`() {
        // Clock changes and restored backups both produce these.
        val future = skipPenalty(skip(count = 3, daysAgo = -30.0), now)
        assertEquals(1.0, future, 0.001)
    }

    @Test
    fun `artist penalties sum each track and ignore unnamed ones`() {
        val penalties = artistSkipPenalties(
            listOf(
                skip(count = 3, daysAgo = 0.0, artist = "Burial"),
                skip(count = 3, daysAgo = 0.0, artist = "burial"),
                skip(count = 3, daysAgo = 0.0, artist = ""),
            ),
            now,
        )
        assertEquals(setOf("burial"), penalties.keys)
        assertEquals(2.0, penalties.getValue("burial"), 0.001)
    }
}
