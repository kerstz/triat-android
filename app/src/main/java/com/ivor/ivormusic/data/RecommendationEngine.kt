package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Local recommendation engine.
 *
 * Builds a "taste profile" from what the user actually does in the app —
 * play history ([StatsRepository]), liked songs ([LikedSongsRepository]) and
 * search history ([SearchHistoryRepository]) — and uses it to personalize
 * both the home feed and the auto-queue, without requiring a YouTube login.
 *
 * Scoring: each play contributes a recency-decayed weight (half-life
 * [HALF_LIFE_DAYS]), so heavy rotation from months ago fades out while this
 * week's obsession dominates. Liked songs get a flat bonus on top.
 */
class RecommendationEngine(
    context: Context,
    private val youTubeRepository: YouTubeRepository
) {
    private val statsRepository = StatsRepository(context)
    private val searchHistoryRepository = SearchHistoryRepository(context)
    private val likedSongsRepository = LikedSongsRepository(context)
    private val playlistRepository = PlaylistRepository(context)
    private val tasteSignals = TasteSignals(context)
    private val tasteGraph = TasteGraph(context, youTubeRepository)

    data class TasteProfile(
        /** Artist names ranked by recency-weighted listening, best first. */
        val topArtists: List<String>,
        /**
         * The same artists with their scores, for anything that weights by
         * how much an artist is actually listened to rather than treating the
         * top eight as equals - which the artist graph does, since a neighbour
         * named by the favourite artist is a better bet than one named by the
         * eighth.
         */
        val artistAffinity: List<Pair<String, Double>> = emptyList(),
        /** The user's highest-scoring YouTube songs (radio seed candidates). */
        val topSongs: List<PlayHistoryEntry>,
        /** Most recent search queries, newest first. */
        val recentSearches: List<String>
    ) {
        fun isEmpty() = topArtists.isEmpty() && topSongs.isEmpty() && recentSearches.isEmpty()
    }

    /**
     * The three discovery shelves, carried together because they are fetched
     * together and a partial result is normal: any one of them can come back
     * empty without the other two being wrong.
     */
    data class DiscoveryCollections(
        val albums: List<PlaylistDisplayItem> = emptyList(),
        val playlists: List<PlaylistDisplayItem> = emptyList(),
        val artists: List<ArtistItem> = emptyList()
    ) {
        fun isEmpty() = albums.isEmpty() && playlists.isEmpty() && artists.isEmpty()
    }

    companion object {
        /** A play from two weeks ago counts half as much as one from today. */
        private const val HALF_LIFE_DAYS = 14.0

        /** Flat score bonus for songs the user explicitly liked. */
        private const val LIKED_BONUS = 3.0

        private const val MAX_ARTISTS = 8
        private const val MAX_TOP_SONGS = 10
        private const val MAX_RECENT_SEARCHES = 5
        private const val MS_PER_DAY = 86_400_000.0

        // Cold start, for someone who has played nothing yet. Deliberately
        // undated: "albums 2026" is wrong every January and reads as a stale
        // app long before anyone thinks to fix it.
        private const val COLD_START_ALBUMS = "new album releases"
        private const val COLD_START_PLAYLISTS = "top hits playlist"
        private const val COLD_START_ARTISTS = "popular artists"

        /** Graph neighbours taken in rank order, then at random from the tail. */
        private const val GRAPH_EXPLOIT_ARTISTS = 6
        private const val GRAPH_EXPLORE_ARTISTS = 3

        /**
         * Source weights, on the graph's own scale.
         *
         * A radio outranks a bare search because it is seeded by a track the
         * listener chose, while a search only repeats words they typed; both
         * sit under the graph, whose scores carry the summed affinity of every
         * favourite that pointed at the artist.
         */
        private const val RADIO_WEIGHT = 1.2
        private const val SEARCH_WEIGHT = 0.6

        /** Places any one artist may hold in the feed. */
        private const val MAX_PER_ARTIST = 2

        /**
         * How damped a track has to be before the auto-queue refuses it.
         *
         * Higher than nothing on purpose: a single old skip should not stop a
         * track appearing in a queue the listener did not curate, where the
         * cost of a wrong guess is one tap.
         */
        private const val QUEUE_SKIP_CUTOFF = 0.6
    }

    suspend fun buildTasteProfile(): TasteProfile = withContext(Dispatchers.Default) {
        val history = statsRepository.loadHistory()
        val likedIds = likedSongsRepository.getAllLikedSongIds()
        val now = System.currentTimeMillis()

        // Recency-weighted score per song. History is newest-first, so the
        // first entry seen for an id carries the freshest metadata.
        val songScores = HashMap<String, Double>()
        val entryById = HashMap<String, PlayHistoryEntry>()
        for (entry in history) {
            val ageDays = (now - entry.timestamp).coerceAtLeast(0) / MS_PER_DAY
            val weight = 0.5.pow(ageDays / HALF_LIFE_DAYS)
            songScores.merge(entry.songId, weight, Double::plus)
            entryById.putIfAbsent(entry.songId, entry)
        }
        for (id in likedIds) {
            if (entryById.containsKey(id)) {
                songScores.merge(id, LIKED_BONUS, Double::plus)
            }
        }

        // Include tracks from user playlists as taste seeds. Read from disk
        // first: this instance has no scope to follow the Wi-Fi sync with, and
        // its own cache would not show playlists received since it was built.
        playlistRepository.refreshPlaylists()
        val playlistSongs = playlistRepository.userPlaylists.value.flatMap { it.songs }
        for (song in playlistSongs) {
            if (song.source == SongSource.YOUTUBE && song.id.isNotBlank()) {
                songScores.merge(song.id, 2.5, Double::plus)
                if (!entryById.containsKey(song.id)) {
                    entryById[song.id] = PlayHistoryEntry(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        duration = song.duration,
                        timestamp = now,
                        thumbnailUrl = song.thumbnailUrl,
                        source = SongSource.YOUTUBE
                    )
                }
            }
        }

        val rankedIds = songScores.entries.sortedByDescending { it.value }.map { it.key }

        val topSongs = rankedIds.asSequence()
            .mapNotNull { entryById[it] }
            .filter { it.source == SongSource.YOUTUBE }
            .take(MAX_TOP_SONGS)
            .toList()

        val artistScores = HashMap<String, Double>()
        for ((id, score) in songScores) {
            val artist = entryById[id]?.artist ?: continue
            if (isUnknownArtist(artist)) continue
            artistScores.merge(artist, score, Double::plus)
        }
        val rankedArtists = artistScores.entries
            .sortedByDescending { it.value }
            .take(MAX_ARTISTS)
            .map { it.key to it.value }
        val topArtists = rankedArtists.map { it.first }

        TasteProfile(
            topArtists = topArtists,
            artistAffinity = rankedArtists,
            topSongs = topSongs,
            recentSearches = searchHistoryRepository.getHistory().take(MAX_RECENT_SEARCHES)
        )
    }

    /**
     * Songs to append to the playing queue.
     *
     * Prefers YouTube's actual related-songs radio for the current track;
     * falls back to searches seeded by the user's top artists, then to a
     * generic similarity search.
     */
    suspend fun getQueueContinuation(
        currentSong: Song?,
        excludeIds: Set<String>,
        limit: Int = 10
    ): List<Song> {
        val results = mutableListOf<Song>()

        // 1. Real related-songs radio for the current track
        if (currentSong != null && currentSong.source == SongSource.YOUTUBE) {
            results += youTubeRepository.getRelatedSongs(currentSong.id)
        }

        // 2. The artists next to this listener's favourites, which keeps an
        // auto-queue widening rather than circling the same names. Falls back
        // to artist searches when the graph has nothing - a fresh profile, or
        // an artist with no "fans might also like" shelf.
        if (results.count { it.id !in excludeIds } < limit) {
            val profile = buildTasteProfile()
            val neighbours = runCatching {
                tasteGraph.neighbours(profile.artistAffinity, maxSeeds = 3, limit = 4)
            }.getOrDefault(emptyList())
            if (neighbours.isNotEmpty()) {
                results += tasteGraph.songsFrom(neighbours.shuffled().take(2)).map { it.song }
            }
            if (results.count { it.id !in excludeIds } < limit) {
                // Shuffled so consecutive loads don't always seed from the same artist.
                for (artist in profile.topArtists.shuffled().take(2)) {
                    results += youTubeRepository.search("$artist songs", YouTubeRepository.FILTER_SONGS)
                    if (results.count { it.id !in excludeIds } >= limit) break
                }
            }
        }

        // 3. Last resort: similarity search on the current song
        if (results.isEmpty() && currentSong != null) {
            results += youTubeRepository.search(
                "songs like ${currentSong.title} ${currentSong.artist}",
                YouTubeRepository.FILTER_SONGS
            )
        }

        // A track turned away from is not a track to queue up next. The
        // penalty is the same one the discovery feed ranks by, so the two
        // surfaces cannot disagree about what was rejected.
        val skips = tasteSignals.skips()
        return results.asSequence()
            .filter { it.id !in excludeIds }
            .distinctBy { it.id }
            .filter { tasteSignals.penaltyFor(skips[it.id]) < QUEUE_SKIP_CUTOFF }
            .take(limit)
            .toList()
    }

    /**
     * Personalized home feed for logged-out users: interleaved results from
     * the user's top artists, their recent searches, and a radio seeded by a
     * favourite song (for discovery beyond exact-artist matches).
     */
    suspend fun getHomeRecommendations(limit: Int = 30): List<Song> = coroutineScope {
        val profile = buildTasteProfile()

        // The account's own YouTube Music home feed (FEmusic_home). Google
        // personalises it server-side from the signed-in cookies, so it is the
        // strongest signal when signed in; signed out it returns trending as its
        // own fallback. Fetched in parallel with the local-taste seeds below so
        // it never slows the others down. Empty and harmless on failure.
        val accountHomeJob = async {
            runCatching { youTubeRepository.getRecommendations() }.getOrDefault(emptyList())
        }

        // No local taste signal yet (fresh install, or signed in but nothing
        // played): lean entirely on the account feed, which is why the home is
        // no longer empty before the first play.
        if (profile.isEmpty()) {
            return@coroutineScope accountHomeJob.await().take(limit)
        }

        // 1. YouTube related-songs radios seeded by user's top played/liked tracks
        val radioJobs = profile.topSongs.take(4).map { seed ->
            async {
                runCatching { youTubeRepository.getRelatedSongs(seed.songId) }
                    .getOrDefault(emptyList())
            }
        }

        // 2. Searches seeded by user's top artists
        val seedQueries = buildList {
            profile.topArtists.take(4).forEach { add("$it songs") }
            profile.recentSearches.take(2).forEach { add(it) }
        }

        val searchJobs = seedQueries.map { query ->
            async {
                runCatching { youTubeRepository.search(query, YouTubeRepository.FILTER_SONGS) }
                    .getOrDefault(emptyList())
            }
        }

        val radioBuckets = radioJobs.map { it.await() }
        val searchBuckets = searchJobs.map { it.await() }
        val accountHome = accountHomeJob.await()

        // Lead with the account feed, then the local-taste buckets, so a signed-in
        // user sees their real YouTube Music recommendations first and the local
        // radios/searches widen the mix rather than replace it.
        val allBuckets = (listOf(accountHome) + radioBuckets + searchBuckets)
            .filter { it.isNotEmpty() }

        val mixed = interleave(allBuckets).distinctBy { it.id }.take(limit)
        if (mixed.isNotEmpty()) return@coroutineScope mixed

        // Everything came back empty (rate limit, offline). Echo the user's own
        // favourites rather than nothing.
        profile.topSongs.map { seed ->
            Song.fromYouTube(
                videoId = seed.songId,
                title = seed.title,
                artist = seed.artist,
                album = seed.album ?: "",
                duration = seed.duration,
                thumbnailUrl = seed.thumbnailUrl
            )
        }
    }

    /**
     * Songs the user has not heard, chosen from what they actually listen to.
     *
     * This is a different question from [getHomeRecommendations], which is
     * "what should this feed look like": that one happily returns the user's
     * own favourites back to them, and for a home feed that is correct.
     * Discovery is only worth the name if what comes back is *new*, so
     * [excludeIds] carries everything the caller already knows about - the
     * device library, likes, downloads, play history - and anything matching
     * is dropped rather than ranked lower.
     *
     * **YouTube's own recommendations skew to what you have already played**,
     * which is why this is not simply a browse call. Three seeds pull in
     * different directions and are interleaved so none of them dominates:
     *
     * - **"latest songs" searches on the top artists** are the half that
     *   actually surfaces *recent* releases. Relevance ranking does the work;
     *   there is no probed InnerTube browse id for new music in this codebase
     *   and inventing one would be writing a request from memory.
     * - **Related-song radios seeded from favourites** reach sideways into
     *   adjacent artists, which is where anything genuinely unfamiliar comes
     *   from - an artist search can only ever return that artist.
     * - **Recent searches** because something typed last week is a taste
     *   signal the play history has not caught up with yet.
     *
     * With no profile at all there is nothing to be personal about, so it asks
     * for new music generally rather than returning nothing.
     */
    suspend fun getDiscoveryRecommendations(
        excludeIds: Set<String> = emptySet(),
        limit: Int = 40
    ): List<Song> = getDiscoveryFeed(excludeIds, limit).map { it.song }

    /**
     * The discovery feed, each entry carrying the artist it was reached
     * through.
     *
     * Three sources pull in different directions and are ranked together
     * rather than interleaved blindly, because they are not equally good:
     *
     * - **The artist graph** ([TasteGraph]) is the one that actually reaches
     *   unfamiliar music. A neighbour named by several favourites at once sits
     *   in the middle of this listener's taste, which no search can express.
     * - **Related-song radios** from favourite tracks reach sideways within a
     *   sound rather than within a name, and catch the artist who has one
     *   track in this world and nothing else like it.
     * - **Recent searches** are intent the play history has not caught up with
     *   yet - something typed last week is a stated interest.
     *
     * Ranking is the score of the source, damped by what the listener has
     * turned away from ([TasteSignals]) and capped per artist so no single
     * discovery owns the screen. A slice of the result is held back for the
     * lower-ranked neighbours, chosen at random: a feed that only ever shows
     * its top answers converges on a narrow band and stops discovering
     * anything, and that slice is what keeps a refresh worth pulling.
     */
    suspend fun getDiscoveryFeed(
        excludeIds: Set<String> = emptySet(),
        limit: Int = 40
    ): List<TasteGraph.Recommendation> = coroutineScope {
        val profile = buildTasteProfile()
        if (profile.isEmpty()) {
            return@coroutineScope youTubeRepository
                .search("new music this week", YouTubeRepository.FILTER_SONGS)
                .filter { it.id !in excludeIds }
                .distinctBy { it.id }
                .take(limit)
                .map { TasteGraph.Recommendation(it, 0.0, null, it.artist) }
        }

        // Round one of the graph: who sits next to the artists being played.
        // Awaited before round two because round two asks about the answers.
        val neighbours = runCatching {
            tasteGraph.neighbours(profile.artistAffinity)
        }.getOrDefault(emptyList())

        // Most of the budget on the best neighbours, the rest on a random pick
        // from the tail - exploration, not noise: every one of them was still
        // named by an artist the listener plays.
        val exploring = neighbours.drop(GRAPH_EXPLOIT_ARTISTS).shuffled().take(GRAPH_EXPLORE_ARTISTS)
        val chosenNeighbours = neighbours.take(GRAPH_EXPLOIT_ARTISTS) + exploring

        val graphJob = async {
            runCatching { tasteGraph.songsFrom(chosenNeighbours) }.getOrDefault(emptyList())
        }
        val radioJobs = profile.topSongs.take(2).map { seed ->
            async {
                runCatching { youTubeRepository.getRelatedSongs(seed.songId) }
                    .getOrDefault(emptyList())
                    .map { song ->
                        TasteGraph.Recommendation(
                            song = song,
                            score = RADIO_WEIGHT,
                            becauseOf = seed.artist.takeIf { !isUnknownArtist(it) },
                            viaArtist = song.artist,
                        )
                    }
            }
        }
        val searchJobs = profile.recentSearches.take(2).map { query ->
            async {
                runCatching { youTubeRepository.search(query, YouTubeRepository.FILTER_SONGS) }
                    .getOrDefault(emptyList())
                    .map { song ->
                        TasteGraph.Recommendation(song, SEARCH_WEIGHT, null, song.artist)
                    }
            }
        }

        val skips = tasteSignals.skips()
        val candidates = (listOf(graphJob) + radioJobs + searchJobs)
            .flatMap { it.await() }
            .filter { it.song.id.isNotBlank() && it.song.id !in excludeIds }
            .distinctBy { it.song.id }
            .map { candidate ->
                val penalty = tasteSignals.penaltyFor(skips[candidate.song.id])
                candidate to candidate.score * (1.0 - penalty)
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first }

        capPerArtist(candidates, MAX_PER_ARTIST).take(limit)
    }

    /**
     * Keeps the ranking but lets no artist hold more than [max] places.
     *
     * A straight sort hands the whole top of the feed to whichever artist the
     * graph liked most, and a screen of one artist is a screen that has
     * discovered one artist. Overflow is not dropped - it falls to the end,
     * where it is still reachable by scrolling.
     */
    private fun capPerArtist(
        items: List<TasteGraph.Recommendation>,
        max: Int
    ): List<TasteGraph.Recommendation> {
        val counts = HashMap<String, Int>()
        val kept = mutableListOf<TasteGraph.Recommendation>()
        val overflow = mutableListOf<TasteGraph.Recommendation>()
        for (item in items) {
            val key = item.viaArtist.lowercase()
            val seen = counts.getOrDefault(key, 0)
            if (seen < max) {
                counts[key] = seen + 1
                kept += item
            } else {
                overflow += item
            }
        }
        return kept + overflow
    }

    /**
     * Albums, playlists and artists to discover, for the shelves under the
     * songs on Spotlight's "For you" tab.
     *
     * A song grid answers "what should I play next" and nothing else: it gives
     * no way to fall into a record, a curated hour, or somebody's whole
     * catalogue, which is most of how people actually find music. These three
     * shelves are that, built from the same taste profile the song half uses.
     *
     * Every query is an ordinary search, deliberately. There is no probed
     * browse id for "new music" in this codebase, and inventing one is writing
     * a request from memory - so this reaches for the endpoint that is already
     * verified rather than the one that would be tidier.
     *
     * Each shelf is independent and each is wrapped in its own runCatching: one
     * refused search costs its own shelf and not the other two, which is what
     * keeps a rate-limited moment from emptying the whole tab.
     */
    suspend fun getDiscoveryCollections(
        excludeCollectionIds: Set<String> = emptySet(),
        excludeArtistNames: Set<String> = emptySet(),
        limitPerShelf: Int = 12
    ): DiscoveryCollections = coroutineScope {
        val profile = buildTasteProfile()
        // Shuffled for the reason the song half shuffles its seeds: a refresh
        // returning the identical three shelves is a refresh that looks like it
        // did not run.
        val seeds = profile.topArtists.shuffled().take(2)
        val querySeeds = if (seeds.isNotEmpty()) seeds else profile.recentSearches.take(1)

        val albumJobs = if (querySeeds.isEmpty()) {
            listOf(async { searchOrEmpty { youTubeRepository.searchAlbums(COLD_START_ALBUMS) } })
        } else {
            querySeeds.map { seed ->
                async { searchOrEmpty { youTubeRepository.searchAlbums("$seed album") } }
            }
        }
        val playlistJobs = if (querySeeds.isEmpty()) {
            listOf(async { searchOrEmpty { youTubeRepository.searchPlaylists(COLD_START_PLAYLISTS) } })
        } else {
            querySeeds.map { seed ->
                async { searchOrEmpty { youTubeRepository.searchPlaylists("$seed playlist") } }
            }
        }
        val artistJobs = if (querySeeds.isEmpty()) {
            listOf(async { searchArtistsOrEmpty(COLD_START_ARTISTS) })
        } else {
            querySeeds.map { seed -> async { searchArtistsOrEmpty(seed) } }
        }

        val excludedNames = excludeArtistNames.map { it.lowercase() }.toSet()

        DiscoveryCollections(
            albums = mergeCollections(albumJobs.map { it.await() }, excludeCollectionIds, limitPerShelf),
            playlists = mergeCollections(
                playlistJobs.map { it.await() },
                excludeCollectionIds,
                limitPerShelf
            ),
            artists = interleaveBy(artistJobs.map { it.await() }) { it.id }
                .filter { it.name.isNotBlank() && it.name.lowercase() !in excludedNames }
                .take(limitPerShelf)
        )
    }

    private suspend fun searchOrEmpty(
        block: suspend () -> List<PlaylistDisplayItem>
    ): List<PlaylistDisplayItem> = runCatching { block() }.getOrDefault(emptyList())

    private suspend fun searchArtistsOrEmpty(query: String): List<ArtistItem> =
        runCatching { youTubeRepository.searchArtists(query) }.getOrDefault(emptyList())

    private fun mergeCollections(
        buckets: List<List<PlaylistDisplayItem>>,
        excludeIds: Set<String>,
        limit: Int
    ): List<PlaylistDisplayItem> =
        interleaveBy(buckets) { it.id }
            .filter { it.name.isNotBlank() && it.id.isNotBlank() && it.id !in excludeIds }
            .take(limit)

    /** [interleave] for anything with an identity, so one seed cannot own a shelf. */
    private fun <T> interleaveBy(buckets: List<List<T>>, id: (T) -> String): List<T> {
        val out = mutableListOf<T>()
        val seen = HashSet<String>()
        var i = 0
        var added = true
        while (added) {
            added = false
            for (bucket in buckets) {
                if (i < bucket.size) {
                    added = true
                    val item = bucket[i]
                    if (seen.add(id(item))) out.add(item)
                }
            }
            i++
        }
        return out
    }

    /** Round-robin merge so one seed doesn't dominate the top of the feed. */
    private fun interleave(buckets: List<List<Song>>): List<Song> {
        val out = mutableListOf<Song>()
        var i = 0
        var added = true
        while (added) {
            added = false
            for (bucket in buckets) {
                if (i < bucket.size) {
                    out.add(bucket[i])
                    added = true
                }
            }
            i++
        }
        return out
    }
}
