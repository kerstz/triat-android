package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure routing logic that keeps SoundCloud tracks on their own path:
 * identification by URL id and the factory that stamps the source. Playback
 * resolution and the Wi-Fi sync both branch on these, so they are worth pinning.
 */
class SoundCloudSourceTest {

    @Test
    fun `soundcloud track urls are recognised`() {
        assertTrue(SoundCloudRepository.isSoundCloudId("https://soundcloud.com/olise/gia-nhu"))
        assertTrue(SoundCloudRepository.isSoundCloudId("http://soundcloud.com/user/track"))
        assertTrue(SoundCloudRepository.isSoundCloudId("https://m.soundcloud.com/user/track"))
    }

    @Test
    fun `youtube ids and local ids are not soundcloud`() {
        assertFalse(SoundCloudRepository.isSoundCloudId("dQw4w9WgXcQ"))
        assertFalse(SoundCloudRepository.isSoundCloudId("12345"))
        assertFalse(SoundCloudRepository.isSoundCloudId("https://youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `fromSoundCloud stamps the source and carries the url as id`() {
        val url = "https://soundcloud.com/olise/gia-nhu-anh-la-em"
        val song = Song.fromSoundCloud(
            trackUrl = url,
            title = "Gia Nhu Anh La Em",
            artist = "Olise",
            duration = 216_000L,
            thumbnailUrl = "https://i1.sndcdn.com/artwork.jpg"
        )
        assertEquals(SongSource.SOUNDCLOUD, song.source)
        assertEquals(url, song.id)
        assertTrue(SoundCloudRepository.isSoundCloudId(song.id))
    }

    @Test
    fun `fromSoundCloud falls back to unknown artist when blank`() {
        val song = Song.fromSoundCloud(
            trackUrl = "https://soundcloud.com/x/y",
            title = "Track",
            artist = "",
            duration = 1000L,
            thumbnailUrl = null
        )
        assertEquals(UNKNOWN_ARTIST, song.artist)
    }
}
