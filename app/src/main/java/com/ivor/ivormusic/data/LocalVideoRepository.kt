package com.ivor.ivormusic.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The device's own video files, read straight from MediaStore.
 *
 * Deliberately a plain per-caller instance rather than a process-wide store:
 * nothing here is written, so two readers cannot disagree, and the list is
 * re-read whenever the Library asks rather than cached across a scan that the
 * system may have run in between.
 */
class LocalVideoRepository(private val context: Context) {


    companion object {
        const val UNKNOWN_FOLDER_NAME = "Other"

        /**
         * Group a flat list into folder cards, ordered by most recent activity.
         *
         * Folders are keyed by bucket id where MediaStore gave one and by name
         * otherwise, so the fallback rows above group with each other rather
         * than each becoming its own single-item folder.
         */
        fun foldersOf(videos: List<LocalVideo>): List<LocalVideoFolder> =
            videos
                .groupBy { it.bucketId ?: it.folderName.hashCode().toLong() }
                .map { (_, inFolder) ->
                    // Already newest-first from the query, but a caller may
                    // hand this a re-sorted list and the cover must stay the
                    // newest frame either way.
                    val newest = inFolder.maxByOrNull { it.dateAddedMs ?: 0L }
                    LocalVideoFolder(
                        bucketId = inFolder.first().bucketId,
                        name = inFolder.first().folderName,
                        videoCount = inFolder.size,
                        coverUri = newest?.uri,
                        newestDateMs = newest?.dateAddedMs,
                    )
                }
                .sortedWith(
                    compareByDescending<LocalVideoFolder> { it.newestDateMs ?: 0L }
                        .thenBy { it.name.lowercase() }
                )

        /** Apply one of the sheet's sort orders. */
        fun sorted(videos: List<LocalVideo>, sort: LocalVideoSort): List<LocalVideo> = when (sort) {
            LocalVideoSort.RECENT -> videos.sortedByDescending { it.dateAddedMs ?: 0L }
            LocalVideoSort.NAME -> videos.sortedBy { it.title.lowercase() }
            LocalVideoSort.DURATION -> videos.sortedByDescending { it.durationMs }
            LocalVideoSort.SIZE -> videos.sortedByDescending { it.sizeBytes }
        }

        /**
         * The videos in one folder, matched the same way [foldersOf] grouped
         * them so a null bucket id still resolves to its own card's contents.
         */
        fun videosIn(videos: List<LocalVideo>, folder: LocalVideoFolder): List<LocalVideo> =
            videos.filter {
                if (folder.bucketId != null) it.bucketId == folder.bucketId
                else it.bucketId == null && it.folderName == folder.name
            }

        /** "1.4 GB", "812 MB" - the size shown under a device video row. */
        fun formatSize(bytes: Long): String = when {
            bytes <= 0L -> ""
            bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> "${bytes / 1_000_000L} MB"
            else -> "${(bytes / 1_000L).coerceAtLeast(1L)} KB"
        }
    }
}

/** Whether the device's videos are readable, and how completely. */
enum class LocalVideoAccess {
    /** Every video on the device is visible. */
    FULL,

    /**
     * Android 14's partial grant: only the files the user picked are visible,
     * and they can widen the selection without going to system settings.
     */
    PARTIAL,

    DENIED,
}
