package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeLinkHttpsTest {
    @Test
    fun `http links are upgraded to https, others are untouched`() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ", YouTubeLinkParser.forceHttps("http://youtu.be/dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=x", YouTubeLinkParser.forceHttps("HTTP://www.youtube.com/watch?v=x"))
        assertEquals("https://youtu.be/a", YouTubeLinkParser.forceHttps("https://youtu.be/a"))
        assertEquals("youtu.be/a", YouTubeLinkParser.forceHttps("youtu.be/a"))
    }
}
