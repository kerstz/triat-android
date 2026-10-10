package com.ivor.ivormusic.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadedAudioMetadataTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun failedMetadataWritePreservesOriginalAndRemovesRejectedCopy() {
        val source = temporaryFolder.newFile("source.m4a").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val result = createMetadataCopy(source, temporaryFolder.root) { copy ->
            copy.writeBytes(byteArrayOf(9, 9))
            error("MP4 offsets rejected")
        }

        assertTrue(result.isFailure)
        assertTrue(source.readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertFalse(
            temporaryFolder.root.listFiles().orEmpty().any { it.name.startsWith("koda_tagged_") }
        )
    }

    @Test
    fun successfulMetadataWriteReturnsCopyAndPreservesOriginal() {
        val source = temporaryFolder.newFile("source.m4a").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val result = createMetadataCopy(source, temporaryFolder.root) { copy ->
            copy.appendBytes(byteArrayOf(5, 6))
        }

        assertTrue(result.isSuccess)
        assertTrue(source.readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertTrue(result.getOrThrow().readBytes().contentEquals(byteArrayOf(1, 2, 3, 4, 5, 6)))
    }
}
