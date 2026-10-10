package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A YouTube history records uploaders, not musicians. Getting this wrong is
 * what made the artist graph browse "nn" and "Music EDM 2022" and come back
 * with nothing, so the cases below are the ones that were actually on the
 * device when that was found.
 */
class MusicArtistNameTest {

    @Test
    fun `the convention is artist dash title`() {
        assertEquals("Daft Punk", musicArtistOf("Daft Punk - One More Time", "convar HUN"))
    }

    @Test
    fun `a title with no separator leaves the channel alone`() {
        assertEquals("Olise", musicArtistOf("Giá Như Anh Là Em Lofi", "Olise"))
    }

    @Test
    fun `a bare dash inside a name is not a separator`() {
        assertEquals("nn", musicArtistOf("Blink-182 Adam's Song", "nn"))
        assertEquals("Jay-Z", musicArtistOf("Jay-Z - Takeover", "somechannel"))
    }

    @Test
    fun `a title leading with its own channel says nothing new`() {
        assertEquals("Daft Punk", musicArtistOf("Daft Punk - Veridis Quo", "Daft Punk"))
    }

    @Test
    fun `a first clause too long to be a name is refused`() {
        val sentence = "Best lofi beats to study and relax to all night long - mix 2026"
        assertEquals("Music EDM 2022", musicArtistOf(sentence, "Music EDM 2022"))
    }

    @Test
    fun `trailing punctuation is trimmed off the name`() {
        assertEquals("SOPHIE", musicArtistOf("SOPHIE | - VYZEE", "nn"))
    }

    @Test
    fun `a separator at the start is not a name`() {
        assertEquals("nn", musicArtistOf(" - VYZEE", "nn"))
    }

    @Test
    fun `no title at all falls back to the channel`() {
        assertEquals("nn", musicArtistOf(null, "nn"))
        assertEquals("", musicArtistOf(null, null))
    }

    @Test
    fun `the key folds case so one artist is one bucket`() {
        assertEquals(artistKey("Burial - Archangel", "x"), artistKey("burial - Untrue", "y"))
    }
}
