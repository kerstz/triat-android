package com.ivor.ivormusic.data

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

/**
 * Writes portable metadata into a completed M4A before it is published.
 *
 * Koda's private downloads JSON is an index, not a substitute for file tags:
 * files in public storage must retain their identity when opened in another
 * player or after Koda is uninstalled. Artwork therefore lives in the M4A
 * itself as well as in the optional companion file Koda keeps for fast
 * offline display.
 */
internal object DownloadedAudioMetadata {

    /**
     * Add portable tags without ever putting the downloaded media at risk.
     *
     * Some YouTube M4A layouts leave no free atom large enough for embedded
     * artwork. jaudiotagger then shifts the MP4 data and can reject its own
     * rewritten offsets. Performing that operation on the only downloaded
     * copy made an otherwise playable song fail. The caller can now publish
     * [sourceAudio] whenever this best-effort result fails.
     */
    fun writeCopy(
        sourceAudio: File,
        tempDirectory: File,
        song: Song,
        artworkFile: File?
    ): Result<File> = createMetadataCopy(sourceAudio, tempDirectory) { taggedCopy ->
        write(
            audioFile = taggedCopy,
            song = song,
            artworkFile = artworkFile
        )
    }

    private fun write(
        audioFile: File,
        song: Song,
        artworkFile: File?
    ) {
        val taggedAudio = AudioFileIO.read(audioFile)
        val tag = taggedAudio.tagOrCreateAndSetDefault

        tag.setField(FieldKey.TITLE, song.title.ifBlank { audioFile.nameWithoutExtension })
        song.artist.takeIf(String::isNotBlank)?.let { artist ->
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM_ARTIST, artist)
        }
        song.album.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.ALBUM, it) }

        artworkFile?.takeIf { it.isFile && it.length() > 0L }?.let { cover ->
            tag.deleteArtworkField()
            tag.setField(
                ArtworkFactory.createArtworkFromFile(cover).apply {
                    description = "Cover"
                    pictureType = FRONT_COVER_PICTURE_TYPE
                }
            )
        }

        taggedAudio.commit()
    }

    private const val FRONT_COVER_PICTURE_TYPE = 3
}

/**
 * Copy first, enrich second, and remove a rejected copy. Kept independent of
 * jaudiotagger so the non-destructive fallback contract has a JVM unit test.
 */
internal fun createMetadataCopy(
    sourceAudio: File,
    tempDirectory: File,
    writer: (File) -> Unit
): Result<File> {
    var taggedCopy: File? = null
    return runCatching {
        File.createTempFile("koda_tagged_", ".m4a", tempDirectory).also { copy ->
            taggedCopy = copy
            sourceAudio.copyTo(copy, overwrite = true)
            writer(copy)
        }
    }.onFailure {
        taggedCopy?.delete()
    }
}
