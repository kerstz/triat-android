package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * What the listener turned away from.
 *
 * Every other signal the app keeps is positive - plays, likes, playlists,
 * searches - so a recommendation engine built on them can only ever argue for
 * things and never against one. That is why a song dismissed on the first
 * listen used to come back the next day under a different shelf: nothing in
 * the app had recorded that it had been heard and rejected.
 *
 * **A skip is a weak signal and is treated as one.** Someone skips a track
 * they love because it does not fit the moment, because the chorus is three
 * minutes away, or because they are hunting for something specific. So one
 * skip only dampens; it takes a few to suppress, the weight decays like the
 * play weights do, and a song that is also liked or played through is never
 * suppressed at all - the positive signal is the stronger statement and the
 * engine resolves the contradiction in its favour.
 *
 * Stored in ordinary preferences rather than a file: this is a bounded set of
 * short ids, read on every ranking pass and written a few times an hour.
 */
class TasteSignals(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** One skipped track: how often, how recently, and by whom. */
    data class Skip(val songId: String, val artist: String, val count: Int, val lastAtMs: Long)

    /**
     * Record that [song] was passed over before it had a fair hearing.
     *
     * Incognito is honoured here rather than at the call site for the reason
     * every other store does it: the gate belongs where the write is, or the
     * next surface to record a skip has to remember to ask.
     */
    suspend fun recordSkip(song: Song) = withContext(Dispatchers.IO) {
        if (IncognitoMode.isEnabled(appContext)) return@withContext
        if (song.source != SongSource.YOUTUBE || song.id.isBlank()) return@withContext
        val existing = readSkips()[song.id]
        val entry = Skip(
            songId = song.id,
            artist = song.artist,
            count = (existing?.count ?: 0) + 1,
            lastAtMs = System.currentTimeMillis(),
        )
        val updated = readSkips().toMutableMap().apply { put(song.id, entry) }
        writeSkips(updated.values.sortedByDescending { it.lastAtMs }.take(MAX_ENTRIES))
    }

    /** Clears one track's skips, for a song that has since been played through. */
    suspend fun forget(songId: String) = withContext(Dispatchers.IO) {
        val updated = readSkips().toMutableMap().apply { remove(songId) }
        writeSkips(updated.values.toList())
    }

    suspend fun skips(): Map<String, Skip> = withContext(Dispatchers.IO) { readSkips() }

    /**
     * How much a track's score should be cut, from 0 (untouched) to 1 (gone).
     *
     * A single skip costs a little, three or more cost nearly everything, and
     * the whole penalty fades with the same half-life the play weights use, so
     * a song rejected two months ago is offered again rather than blacklisted
     * for life. Taste changes; a store that never forgets would not let it.
     */
    fun penaltyFor(skip: Skip?, nowMs: Long = System.currentTimeMillis()): Double {
        if (skip == null) return 0.0
        val ageDays = (nowMs - skip.lastAtMs).coerceAtLeast(0L) / MS_PER_DAY
        val decay = 0.5.pow(ageDays / SKIP_HALF_LIFE_DAYS)
        val strength = (skip.count.toDouble() / SKIPS_TO_SUPPRESS).coerceAtMost(1.0)
        return strength * decay
    }

    /**
     * Artists whose tracks keep being skipped, with their penalties summed.
     *
     * Used to damp a *neighbour* artist the graph suggests, never one the user
     * plays: skipping three tracks by an artist you listen to daily says those
     * tracks were wrong, not the artist.
     */
    fun artistPenalties(skips: Collection<Skip>, nowMs: Long = System.currentTimeMillis()): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (skip in skips) {
            if (skip.artist.isBlank()) continue
            out.merge(skip.artist.lowercase(), penaltyFor(skip, nowMs), Double::plus)
        }
        return out
    }

    private fun readSkips(): Map<String, Skip> {
        val raw = prefs.getStringSet(KEY_SKIPS, emptySet()) ?: emptySet()
        return raw.mapNotNull { decode(it) }.associateBy { it.songId }
    }

    private fun writeSkips(entries: List<Skip>) {
        prefs.edit()
            .putStringSet(KEY_SKIPS, entries.map(::encode).toSet())
            .apply()
    }

    // songId|count|lastAtMs|artist - the artist last because it is the only
    // field that can contain anything, including the separator.
    private fun encode(skip: Skip): String =
        "${skip.songId}|${skip.count}|${skip.lastAtMs}|${skip.artist}"

    private fun decode(raw: String): Skip? {
        val parts = raw.split('|', limit = 4)
        if (parts.size < 4) return null
        val count = parts[1].toIntOrNull() ?: return null
        val at = parts[2].toLongOrNull() ?: return null
        return Skip(parts[0], parts[3], count, at)
    }

    companion object {
        private const val PREFS = "taste_signals"
        private const val KEY_SKIPS = "skips"

        /** Bounded: the oldest skips are the ones already decayed to nothing. */
        private const val MAX_ENTRIES = 400

        /** Skips needed before a track is suppressed outright. */
        private const val SKIPS_TO_SUPPRESS = 3.0

        /** Longer than the play half-life: a rejection ages out, slowly. */
        private const val SKIP_HALF_LIFE_DAYS = 45.0

        private const val MS_PER_DAY = 86_400_000.0

        /**
         * A track dropped inside this window was not given a chance, so the
         * turn away is about the music rather than about the listening.
         *
         * Matched to the fifteen seconds a play is counted after: below it
         * nothing is recorded as a play, so without this the first fifteen
         * seconds of every track were invisible to the engine either way.
         */
        const val SKIP_WINDOW_MS = 15_000L
    }
}
