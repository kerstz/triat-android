package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The diversity cap is what separates a discovery feed from a page about one
 * artist, and it is invisible until a listener's graph is lopsided enough to
 * produce one - which is exactly when nobody is looking.
 */
class DiscoveryRankingTest {

    private fun rec(artist: String, id: String) = TasteGraph.Recommendation(
        song = Song(
            id = id,
            title = id,
            artist = artist,
            album = "",
            duration = 0L,
            source = SongSource.YOUTUBE,
        ),
        score = 1.0,
        becauseOf = null,
        viaArtist = artist,
    )

    @Test
    fun `no artist holds more than the cap at the top of the feed`() {
        val capped = capPerArtist(
            listOf(
                rec("Mica Levi", "a"),
                rec("Mica Levi", "b"),
                rec("Mica Levi", "c"),
                rec("Oneohtrix", "d"),
            ),
            max = 2,
        )
        assertEquals(listOf("a", "b", "d", "c"), capped.map { it.song.id })
    }

    @Test
    fun `overflow falls to the end rather than being dropped`() {
        val items = (1..5).map { rec("Arca", "t$it") }
        val capped = capPerArtist(items, max = 2)
        assertEquals(items.size, capped.size)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), capped.map { it.song.id })
    }

    @Test
    fun `the cap is per artist, not per spelling`() {
        val capped = capPerArtist(
            listOf(rec("Burial", "a"), rec("burial", "b"), rec("BURIAL", "c")),
            max = 2,
        )
        assertEquals(listOf("a", "b", "c"), capped.map { it.song.id })
        // The third is overflow: it is last rather than absent.
        assertEquals("c", capped.last().song.id)
    }

    @Test
    fun `ranking order is preserved inside the cap`() {
        val capped = capPerArtist(
            listOf(rec("A", "1"), rec("B", "2"), rec("A", "3"), rec("B", "4")),
            max = 2,
        )
        assertEquals(listOf("1", "2", "3", "4"), capped.map { it.song.id })
    }
}
