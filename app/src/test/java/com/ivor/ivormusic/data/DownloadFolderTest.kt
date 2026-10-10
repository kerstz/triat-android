package com.ivor.ivormusic.data

import android.os.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFolderTest {
    private val root = Environment.DIRECTORY_DOWNLOADS

    @Test
    fun `new downloads go to the Triat folder`() {
        assertEquals("$root/Triat/Music", DownloadMediaType.MUSIC.relativePath)
    }

    @Test
    fun `downloads made before the rename are still ours`() {
        assertTrue(DownloadMediaType.MUSIC.isOwnFolder("$root/Koda/Music/"))
        assertTrue(DownloadMediaType.MUSIC.isOwnFolder("$root/Triat/Music/"))
    }

    @Test
    fun `other folders are not ours`() {
        assertFalse(DownloadMediaType.MUSIC.isOwnFolder("$root/Other/Music/"))
    }
}
