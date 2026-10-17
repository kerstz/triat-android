package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeTitleParserTest {

    private fun clean(raw: String) = YouTubeTitleParser.cleanTitle(raw)

    @Test
    fun stripsBracketedPackaging() {
        val cases = mapOf(
            "Daft Punk - One More Time (Official Video)" to "Daft Punk - One More Time",
            "Song (Official Music Video)" to "Song",
            "Song [Official Music Video]" to "Song",
            "Song (Official HD Video)" to "Song",
            "Song (Official Video HD)" to "Song",
            "Song [Official Audio]" to "Song",
            "Song (Official Lyric Video)" to "Song",
            "Song (Official Visualizer)" to "Song",
            "Song (Visualiser)" to "Song",
            "Song (Lyrics)" to "Song",
            "Song [Lyrics]" to "Song",
            "Song (Lyric Video)" to "Song",
            "Song (Lyrics Video)" to "Song",
            "Song (Audio)" to "Song",
            "Song [4K]" to "Song",
            "Song (HD)" to "Song",
            "Song [MV]" to "Song",
            "Song (Clip Officiel)" to "Song",
            "Song (Vidéo Officielle)" to "Song",
            "Song (Video Oficial)" to "Song",
            "Song (Letra)" to "Song",
            "Song (Official)" to "Song",
        )
        for ((raw, expected) in cases) assertEquals(raw, expected, clean(raw))
    }

    @Test
    fun stripsSeveralGroupsInARow() {
        assertEquals("Song", clean("Song (Official Video) [HD]"))
        assertEquals("Song", clean("Song [Official Audio] (Lyrics)"))
    }

    @Test
    fun stripsSeparatorTrailers() {
        assertEquals("Artist - Song", clean("Artist - Song | Official Video"))
        assertEquals("Artist - Song", clean("Artist - Song - Official Audio"))
        assertEquals("Artist - Song", clean("Artist - Song // Lyrics"))
        assertEquals("Artist - Song", clean("Artist - Song | Official Music Video [4K]"))
        assertEquals("Artist - Song", clean("Artist - Song - Lyric Video"))
    }

    @Test
    fun stripsShoutedChannelTrailers() {
        assertEquals("Artist - Song", clean("Artist - Song // LETRAS4K"))
        assertEquals("Artist - Song", clean("Artist - Song (Lyrics) // LETRAS4K"))
    }

    @Test
    fun keepsGenuineParentheses() {
        val titles = listOf(
            "(I Can't Get No) Satisfaction",
            "Song (feat. Someone)",
            "Song (Live at Wembley)",
            "Song (Remix)",
            "Song (Acoustic Version)",
            "Song (Radio Edit)",
            "Song (Taylor's Version)",
            "Song [Remastered 2011]",
            "Song (Part 2)",
        )
        for (t in titles) assertEquals(t, t, clean(t))
    }

    @Test
    fun keepsTitlesThatMerelyContainTheWords() {
        val titles = listOf(
            "Video Killed the Radio Star",
            "Audio Bullys - Shot You Down",
            "Official Secrets",
            "HD Girl",
            "Lyrics",
            "Artist - Video",
            "Artist - Audio",
            "Clip Clop",
            "AC/DC - Back In Black",
            "Jay-Z - 99 Problems",
            "Song // Live",
        )
        for (t in titles) assertEquals(t, t, clean(t))
    }

    @Test
    fun neverReturnsAnEmptyTitle() {
        assertEquals("(Official Video)", YouTubeTitleParser.parseTitleAndArtist("(Official Video)", null).first)
        assertEquals("Song", YouTubeTitleParser.parseTitleAndArtist("  Song (Lyrics) ", "ArtistVEVO").first)
    }

    @Test
    fun normalisesTheAuthor() {
        assertEquals("Artist", YouTubeTitleParser.parseTitleAndArtist("Song", "ArtistVEVO").second)
        assertEquals("Artist", YouTubeTitleParser.parseTitleAndArtist("Song", "Artist - Topic").second)
        assertEquals(UNKNOWN_ARTIST, YouTubeTitleParser.parseTitleAndArtist("Song", null).second)
    }
}
