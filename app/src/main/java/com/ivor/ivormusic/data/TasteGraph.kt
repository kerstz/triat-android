package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * The artists next to the ones you listen to, ranked by how many of them
 * point there.
 *
 * **This is the part a search cannot do.** Searching "<artist> latest songs"
 * can only ever return that artist, so a feed built from searches is the
 * listener's own library with newer dates on it - which is exactly how the
 * discovery shelf used to read. YouTube Music publishes a "Fans might also
 * like" shelf on every artist page, and that shelf is an edge in a graph:
 * following it from the artists someone actually plays is what reaches music
 * they have never heard.
 *
 * **Co-citation is the ranking, not popularity.** An artist named by one
 * favourite is a guess; an artist named by three of them sits in the middle
 * of that listener's taste and is a much better bet than whoever happens to
 * have the largest audience. So a neighbour's score is the summed affinity of
 * the artists pointing at it, with a bonus that grows with the number of
 * distinct pointers - and a neighbour nobody else names can still rank, which
 * is where the genuinely unfamiliar comes from.
 *
 * Every call is a plain artist browse, already verified in this codebase. No
 * endpoint here is invented, and the two rounds are bounded: seeds first, then
 * the neighbours worth asking about.
 */
class TasteGraph(
    context: Context,
    private val youTubeRepository: YouTubeRepository
) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val signals = TasteSignals(appContext)

    /**
     * One artist adjacent to the listener's taste.
     *
     * [becauseOf] is not decoration: a recommendation that can name the artist
     * it came from is one the listener can agree or disagree with, and the
     * feed stops being a list of strangers.
     */
    data class Neighbour(
        val artistId: String,
        val name: String,
        val score: Double,
        val becauseOf: List<String>,
        val thumbnailUrl: String? = null,
    )

    /**
     * Neighbours of [seeds], best first.
     *
     * [seeds] are artist names weighted by affinity - the taste profile's own
     * ranking - because that is what the play history records. Names are
     * resolved to browse ids once and remembered: the resolution is a search
     * per artist, and an artist's id does not change.
     */
    suspend fun neighbours(
        seeds: List<Pair<String, Double>>,
        maxSeeds: Int = MAX_SEEDS,
        limit: Int = MAX_NEIGHBOURS,
    ): List<Neighbour> = coroutineScope {
        if (seeds.isEmpty()) return@coroutineScope emptyList()

        val chosen = seeds.take(maxSeeds)
        val gate = Semaphore(CONCURRENCY)

        val pages = chosen.map { (name, affinity) ->
            async {
                val id = resolveArtistId(name) ?: return@async null
                val page = gate.withPermit {
                    runCatching { youTubeRepository.getArtistPage(id) }.getOrNull()
                } ?: return@async null
                name to (affinity to page)
            }
        }.mapNotNull { it.await() }

        if (pages.isEmpty()) return@coroutineScope emptyList()

        // The artists already being listened to, so the graph cannot hand back
        // the listener's own rotation as a discovery.
        val known = buildSet {
            seeds.forEach { add(it.first.lowercase()) }
            pages.forEach { add(it.second.second.name.lowercase()) }
        }

        val skipPenalties = signals.artistPenalties(signals.skips().values)

        data class Vote(var score: Double, val pointers: MutableList<Pair<String, Double>>)
        val votes = HashMap<String, Vote>()
        val identity = HashMap<String, SimilarArtist>()

        for ((seedName, pair) in pages) {
            val (affinity, page) = pair
            // Shelf order is roughly confidence, so a neighbour listed first is
            // worth more than the seventh - without this every entry on the
            // shelf arrives equal and the ranking is decided by ties.
            page.similarArtists.forEachIndexed { index, similar ->
                if (similar.id.isBlank() || similar.name.isBlank()) return@forEachIndexed
                if (similar.name.lowercase() in known) return@forEachIndexed
                val positional = 1.0 / (1.0 + index * SHELF_DECAY)
                val vote = votes.getOrPut(similar.id) { Vote(0.0, mutableListOf()) }
                vote.score += affinity * positional
                vote.pointers += seedName to affinity
                identity.putIfAbsent(similar.id, similar)
            }
        }

        votes.entries
            .mapNotNull { (id, vote) ->
                val similar = identity[id] ?: return@mapNotNull null
                // Named by several favourites at once: the strongest thing the
                // graph can say, and the reason this is not just a ranked copy
                // of one artist's shelf.
                val distinct = vote.pointers.map { it.first }.distinct()
                val agreement = 1.0 + CO_CITATION_BONUS * (distinct.size - 1)
                val penalty = skipPenalties[similar.name.lowercase()] ?: 0.0
                val score = vote.score * agreement * (1.0 - penalty.coerceAtMost(MAX_ARTIST_PENALTY))
                if (score <= 0.0) return@mapNotNull null
                Neighbour(
                    artistId = id,
                    name = similar.name,
                    score = score,
                    becauseOf = distinct
                        .sortedByDescending { name ->
                            vote.pointers.firstOrNull { it.first == name }?.second ?: 0.0
                        },
                    thumbnailUrl = similar.thumbnailUrl,
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Songs by [neighbours], carrying the artist each one was reached through.
     *
     * The artist page's top-songs shelf rather than a search: it is the
     * artist's own shelf, ordered roughly by popularity, so a listener meeting
     * someone for the first time meets them at their best rather than at
     * whatever a relevance ranking surfaced.
     *
     * [perArtist] is a cap rather than a target. Two tracks are enough to
     * decide whether an artist is worth following; five would let one
     * neighbour own a screen, which is how a discovery feed stops being one.
     */
    suspend fun songsFrom(
        neighbours: List<Neighbour>,
        perArtist: Int = SONGS_PER_ARTIST,
    ): List<Recommendation> = coroutineScope {
        if (neighbours.isEmpty()) return@coroutineScope emptyList()
        val gate = Semaphore(CONCURRENCY)
        neighbours.map { neighbour ->
            async {
                val page = gate.withPermit {
                    runCatching { youTubeRepository.getArtistPage(neighbour.artistId) }.getOrNull()
                } ?: return@async emptyList()
                page.topSongs.take(perArtist).map { song ->
                    Recommendation(
                        song = song,
                        score = neighbour.score,
                        becauseOf = neighbour.becauseOf.firstOrNull(),
                        viaArtist = neighbour.name,
                    )
                }
            }
        }.flatMap { it.await() }
    }

    /**
     * One recommendation and why it is here.
     *
     * The reason travels with the song rather than being re-derived at the
     * draw site: by the time a card is composed, the artist that led to it is
     * several hops back and nothing on a [Song] records it.
     */
    data class Recommendation(
        val song: Song,
        val score: Double,
        /** The listened-to artist this came from, for "Because you play X". */
        val becauseOf: String?,
        /** The artist being recommended, for grouping and diversity caps. */
        val viaArtist: String,
    )

    /**
     * An artist name's browse id, resolved once and remembered.
     *
     * The play history records names, the graph needs ids, and the bridge is a
     * search per artist - which is why the answer is kept: the id of an artist
     * does not change, and paying a search for it on every refresh is the kind
     * of discretionary fan-out the rate limiter exists to stop.
     *
     * A miss is remembered too, as a blank: an artist that does not resolve -
     * a local file's tag, a mis-spelling - would otherwise cost a search on
     * every pass forever.
     */
    private suspend fun resolveArtistId(name: String): String? = withContext(Dispatchers.IO) {
        val key = KEY_ID_PREFIX + name.lowercase()
        prefs.getString(key, null)?.let { return@withContext it.takeIf { id -> id.isNotBlank() } }

        val found = runCatching { youTubeRepository.searchArtists(name) }
            .getOrDefault(emptyList())
            // The first exact name match, not merely the first result: an
            // artist search for a common name returns covers, tributes and
            // topic channels above the artist often enough to matter.
            .firstOrNull { it.name.equals(name, ignoreCase = true) && it.id.startsWith("UC") }
            ?: runCatching { youTubeRepository.searchArtists(name) }
                .getOrDefault(emptyList())
                .firstOrNull { it.id.startsWith("UC") }

        prefs.edit().putString(key, found?.id.orEmpty()).apply()
        if (found == null) KLog.d(TAG, "No artist id for $name")
        found?.id
    }

    companion object {
        private const val TAG = "TasteGraph"
        private const val PREFS = "taste_graph"
        private const val KEY_ID_PREFIX = "artist_id:"

        /**
         * Seeds asked about per build. Each is one browse, and the top few
         * artists carry most of the affinity anyway - the eighth favourite
         * adds a request and almost no signal.
         */
        private const val MAX_SEEDS = 5

        /** Neighbours kept for the second round, which is one browse each. */
        private const val MAX_NEIGHBOURS = 10

        private const val SONGS_PER_ARTIST = 2

        /** At most this many browses in flight; the rest queue behind them. */
        private const val CONCURRENCY = 4

        /** How fast a neighbour's weight falls down the shelf. */
        private const val SHELF_DECAY = 0.35

        /** Added per extra favourite naming the same neighbour. */
        private const val CO_CITATION_BONUS = 0.6

        /**
         * A skipped artist is damped, never erased. The graph suggested them
         * for a reason, and a listener who skipped two tracks may still want
         * the third - which the cap leaves room for.
         */
        private const val MAX_ARTIST_PENALTY = 0.8
    }
}
