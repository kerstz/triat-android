package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules for plays received from the desktop, on invented data. */
class StatsHistoryMergeTest {

    private val now = 1_700_000_000_000L

    private fun play(id: String, at: Long, durationMs: Long = 183_000L) =
        PlayHistoryEntry(id, "Title $id", "Some Artist", "", at, durationMs)

    private fun merge(existing: List<PlayHistoryEntry>, incoming: List<PlayHistoryEntry>, limit: Int = 100) =
        mergeHistory(existing, incoming, limit, now)

    @Test
    fun `a play with the same song in the same second is the same play`() {
        val mine = play("aaaaaaaaaaa", now - 10_000L + 200L)
        // Same second, other millisecond: the desktop stores fractions of a second.
        val theirs = play("aaaaaaaaaaa", now - 10_000L + 900L)
        val result = merge(listOf(mine), listOf(theirs))
        assertEquals(0, result.added)
        assertEquals(listOf(mine), result.entries)
    }

    @Test
    fun `the next second or another song is a different play`() {
        val mine = play("aaaaaaaaaaa", now - 10_000L)
        val nextSecond = play("aaaaaaaaaaa", now - 9_000L)
        val otherSong = play("bbbbbbbbbbb", now - 10_000L)
        val result = merge(listOf(mine), listOf(nextSecond, otherSong))
        assertEquals(2, result.added)
        assertEquals(3, result.entries.size)
    }

    @Test
    fun `a play received twice is added once`() {
        val theirs = play("aaaaaaaaaaa", now - 5_000L)
        val result = merge(emptyList(), listOf(theirs, theirs.copy(timestamp = theirs.timestamp + 1)))
        assertEquals(1, result.added)
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `nothing new leaves the history untouched`() {
        val existing = listOf(play("aaaaaaaaaaa", now - 1_000L), play("bbbbbbbbbbb", now - 2_000L))
        val result = merge(existing, existing.map { it.copy(timestamp = it.timestamp + 5) })
        assertEquals(0, result.added)
        assertSame(existing, result.entries)
    }

    @Test
    fun `received plays slot into place, newest first`() {
        val existing = listOf(play("aaaaaaaaaaa", now - 1_000L), play("bbbbbbbbbbb", now - 5_000L))
        val incoming = listOf(play("ccccccccccc", now - 3_000L), play("ddddddddddd", now - 9_000L))
        val result = merge(existing, incoming)
        assertEquals(2, result.added)
        assertEquals(
            listOf("aaaaaaaaaaa", "ccccccccccc", "bbbbbbbbbbb", "ddddddddddd"),
            result.entries.map { it.songId },
        )
        // Each keeps the date it was played, none is stamped with now.
        assertEquals(now - 3_000L, result.entries[1].timestamp)
    }

    @Test
    fun `the history never grows past the limit and the newest plays stay`() {
        val existing = List(5) { play("aaaaaaaaaa$it", now - 1_000L * (it + 1)) }
        val incoming = listOf(play("bbbbbbbbbbb", now - 500L), play("ccccccccccc", now - 2_500L))
        val result = merge(existing, incoming, limit = 5)
        assertEquals(5, result.entries.size)
        assertEquals("bbbbbbbbbbb", result.entries.first().songId)
        assertTrue(result.entries.any { it.songId == "ccccccccccc" })
        assertEquals(2, result.added)
        // The two oldest local plays made room.
        assertTrue(result.entries.none { it.songId == "aaaaaaaaaa4" || it.songId == "aaaaaaaaaa3" })
    }

    @Test
    fun `received plays older than a full history are not counted as added`() {
        val existing = List(5) { play("aaaaaaaaaa$it", now - 1_000L * (it + 1)) }
        val old = List(3) { play("bbbbbbbbbb$it", now - 100_000L - it) }
        val result = merge(existing, old, limit = 5)
        assertEquals(0, result.added)
        assertEquals(existing.map { it.songId }, result.entries.map { it.songId })
    }

    @Test
    fun `the existing bound holds with the real limit`() {
        val limit = StatsRepository.MAX_HISTORY_ENTRIES
        val existing = List(limit) { play("aaaaaaaaaaa", now - 10_000_000L - it * 1_000L) }
        val incoming = List(3_000) { play("bbbbbbbbbbb", now - 2_000L - it * 1_000L) }
        val result = mergeHistory(existing, incoming, limit, now)
        assertEquals(limit, result.entries.size)
        assertEquals(3_000, result.added)
        // The 3,000 newest of the desktop all fit, the oldest 3,000 local plays made room.
        assertEquals(2_000, result.entries.count { it.songId == "aaaaaaaaaaa" })
        assertEquals(result.entries.sortedByDescending { it.timestamp }, result.entries)
    }

    @Test
    fun `a play with no date or dated in the future is dropped`() {
        val undated = play("aaaaaaaaaaa", 0L)
        val future = play("bbbbbbbbbbb", now + 60 * 60_000L)
        val nearlyNow = play("ccccccccccc", now + 60_000L)
        val result = merge(emptyList(), listOf(undated, future, nearlyNow))
        assertEquals(listOf("ccccccccccc"), result.entries.map { it.songId })
        assertEquals(1, result.added)
    }

    @Test
    fun `an empty history takes everything received`() {
        val incoming = List(4) { play("aaaaaaaaaa$it", now - 1_000L * (it + 1)) }
        val result = merge(emptyList(), incoming.reversed())
        assertEquals(4, result.added)
        assertEquals(incoming, result.entries)
    }
}
