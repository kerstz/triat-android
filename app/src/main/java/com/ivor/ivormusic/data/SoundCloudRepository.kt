package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudService
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

/**
 * SoundCloud as a second source, through NewPipe's SoundCloud extractor.
 *
 * The service is constructed directly rather than taken from NewPipe's
 * [org.schabi.newpipe.extractor.ServiceList], the same choice [YouTubeRepository]
 * makes for YouTube: touching `ServiceList` would pull every service into the
 * build, so R8 could no longer discard PeerTube, Bandcamp and MediaCCC. The id
 * mirrors NewPipe's own registry, where SoundCloud is 1.
 *
 * A SoundCloud [Song] carries the track's web URL as its id (not an 11-char
 * video id), so playback resolution routes it here and the Wi-Fi sync leaves it
 * behind - only YouTube tracks travel.
 */
class SoundCloudRepository(context: Context) {

    companion object {
        private val soundcloudService = SoundcloudService(1)

        @Volatile private var initialized = false
        private val initLock = Any()

        /** A SoundCloud [Song] is identified by its soundcloud.com track URL. */
        fun isSoundCloudId(id: String): Boolean =
            id.startsWith("https://soundcloud.com/") ||
                id.startsWith("http://soundcloud.com/") ||
                id.startsWith("https://m.soundcloud.com/")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun ensureNewPipe() {
        if (initialized) return
        synchronized(initLock) {
            if (!initialized) {
                try {
                    NewPipe.init(NewPipeDownloaderImpl(client))
                } catch (_: Exception) {
                    // Already initialized by YouTubeRepository; its singleton is
                    // shared and works for every service.
                }
                initialized = true
            }
        }
    }

    // Pagination state, per query, so "load more" walks pages instead of
    // refetching the first. Held per instance; the one HomeViewModel instance
    // owns both the first search and its continuations.
    private val extractorCache =
        mutableMapOf<String, org.schabi.newpipe.extractor.search.SearchExtractor>()
    private val nextPageCache = mutableMapOf<String, org.schabi.newpipe.extractor.Page?>()

    /** Search SoundCloud tracks. Returns [Song]s with source SOUNDCLOUD. */
    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        ensureNewPipe()
        try {
            // "tracks" is SoundCloud's content filter for songs; an empty filter
            // returns users and playlists too, which crowd out the few tracks on
            // the first page.
            val extractor = soundcloudService.getSearchExtractor(query, listOf("tracks"), "")
            extractor.fetchPage()
            extractorCache[query] = extractor
            nextPageCache[query] =
                if (extractor.initialPage.hasNextPage()) extractor.initialPage.nextPage else null
            extractor.initialPage.items.toSongs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w("SoundCloudRepository", "Search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    /** The next page of a previous [search]; empty once exhausted. */
    suspend fun searchNext(query: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val extractor = extractorCache[query] ?: return@withContext emptyList()
            val pageInfo = nextPageCache[query] ?: return@withContext emptyList()
            val page = extractor.getPage(pageInfo)
            nextPageCache[query] = if (page.hasNextPage()) page.nextPage else null
            page.items.toSongs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w("SoundCloudRepository", "Load more failed for '$query': ${e.message}")
            emptyList()
        }
    }

    private fun List<org.schabi.newpipe.extractor.InfoItem>.toSongs(): List<Song> =
        filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            val url = item.url ?: return@mapNotNull null
            Song.fromSoundCloud(
                trackUrl = url,
                title = item.name ?: return@mapNotNull null,
                artist = item.uploaderName ?: UNKNOWN_ARTIST,
                duration = (item.duration.coerceAtLeast(0)) * 1000L,
                thumbnailUrl = item.thumbnails?.firstOrNull()?.url
            )
        }

    /** SoundCloud's charts (its default kiosk), as a home shelf. */
    suspend fun charts(): List<Song> = withContext(Dispatchers.IO) {
        ensureNewPipe()
        try {
            val extractor = soundcloudService.kioskList.defaultKioskExtractor
            extractor.fetchPage()
            extractor.initialPage.items.toSongs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w("SoundCloudRepository", "Charts failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Resolve a SoundCloud track URL to a playable audio stream URL. These
     * expire, so resolve right before playback, the same as YouTube.
     */
    suspend fun resolveStreamUrl(trackUrl: String): String? = withContext(Dispatchers.IO) {
        ensureNewPipe()
        try {
            val extractor = soundcloudService.getStreamExtractor(trackUrl)
            extractor.fetchPage()
            extractor.audioStreams
                .filter { it.isUrl && !it.content.isNullOrBlank() }
                .maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
                ?.content
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w("SoundCloudRepository", "Resolve failed for '$trackUrl': ${e.message}")
            null
        }
    }
}
