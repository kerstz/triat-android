package com.ivor.ivormusic.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerRulesTest {
    @Test
    fun fadeIsSilentUntilTheLastThirtySeconds() {
        assertEquals(1f, SleepTimerRules.fadeGain(10 * 60_000L), 0f)
        assertEquals(1f, SleepTimerRules.fadeGain(SleepTimerRules.FADE_MS), 0f)
        assertTrue(SleepTimerRules.fadeGain(SleepTimerRules.FADE_MS - 1) < 1f)
    }

    @Test
    fun fadeFallsMonotonicallyToZeroAtTheDeadline() {
        var previous = 1f
        var remaining = SleepTimerRules.FADE_MS
        while (remaining >= 0L) {
            val gain = SleepTimerRules.fadeGain(remaining)
            assertTrue(gain <= previous)
            previous = gain
            remaining -= 250L
        }
        assertEquals(0f, SleepTimerRules.fadeGain(0L), 0f)
        assertEquals(0f, SleepTimerRules.fadeGain(-5_000L), 0f)
    }

    @Test
    fun restoreKeepsAFutureDeadline() {
        val restore = SleepTimerRules.restore(SleepTimerSnapshot(endsAt = 5_000L, endOfTrack = false), nowMs = 1_000L)
        assertEquals(SleepTimerRules.Restore.Duration(5_000L), restore)
    }

    @Test
    fun restoreDropsAnExpiredDeadline() {
        val restore = SleepTimerRules.restore(SleepTimerSnapshot(endsAt = 1_000L, endOfTrack = false), nowMs = 1_000L)
        assertEquals(SleepTimerRules.Restore.None, restore)
    }

    @Test
    fun restoreKeepsEndOfTrackWhateverTheClock() {
        val restore = SleepTimerRules.restore(SleepTimerSnapshot(endsAt = 0L, endOfTrack = true), nowMs = 9_999_999L)
        assertEquals(SleepTimerRules.Restore.EndOfTrack, restore)
    }

    @Test
    fun restoreOfNothingIsNothing() {
        assertEquals(
            SleepTimerRules.Restore.None,
            SleepTimerRules.restore(SleepTimerSnapshot(0L, false), nowMs = 1L)
        )
    }
}
