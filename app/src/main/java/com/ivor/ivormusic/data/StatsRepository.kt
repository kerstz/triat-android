package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

@Serializable
data class PlayHistoryEntry(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val timestamp: Long,
    val duration: Long,
    val thumbnailUrl: String? = null,
    val source: SongSource = SongSource.YOUTUBE
)

@Serializable
data class SongStats(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val playCount: Int,
    val totalPlayTime: Long
)

@Serializable
data class ArtistStats(
    val name: String,
    val playCount: Int,
    val songCount: Int
)

class StatsRepository(private val context: Context) {
    private val historyFile = File(context.filesDir, "play_history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val TAG = "StatsRepository"
    private val mutex = Mutex()

    suspend fun addPlayEvent(song: Song) = withContext(Dispatchers.IO) {
        // Incognito leaves no listening record, which is what makes the stats
        // screen and "recently played" honest about the session it describes.
        if (IncognitoMode.isEnabled(context)) return@withContext
        mutex.withLock {
            try {
                val history = loadHistory().toMutableList()
                
                // DEBOUNCE: Don't add the same song if it was added less than 10 seconds ago
                // This prevents duplicate entries from media item resolution loops.
                val lastEntry = history.firstOrNull()
                if (lastEntry?.songId == song.id && (System.currentTimeMillis() - lastEntry.timestamp) < 10000L) {
                    KLog.d(TAG, "Stats: Debouncing duplicate play event for ${song.title}")
                    return@withLock
                }

                val entry = PlayHistoryEntry(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    timestamp = System.currentTimeMillis(),
                    duration = song.duration,
                    thumbnailUrl = song.thumbnailUrl ?: song.albumArtUri?.toString(),
                    source = song.source
                )
                history.add(0, entry)
                val trimmedHistory = if (history.size > MAX_HISTORY_ENTRIES) {
                    history.take(MAX_HISTORY_ENTRIES)
                } else history
                historyFile.writeText(json.encodeToString(trimmedHistory))
            } catch (e: Exception) {
                KLog.e(TAG, "Error saving play event", e)
            }
        }
    }

    suspend fun loadHistory(): List<PlayHistoryEntry> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<PlayHistoryEntry>>(historyFile.readText())
        } catch (e: Exception) {
            KLog.e(TAG, "Error loading history", e)
            emptyList()
        }
    }

    /**
     * The plays to send to the desktop: YouTube only (local files do not
     * travel), newest first, at most [limit] - the desktop keeps 3,000 itself.
     *
     * The incognito gate is on the way out, in `DesktopSyncRepository`, which
     * sends an empty list from an incognito session; this only reads.
     */
    suspend fun historyForSync(limit: Int = SYNC_HISTORY_LIMIT): List<PlayHistoryEntry> =
        loadHistory()
            .filter { it.source == SongSource.YOUTUBE }
            .sortedByDescending { it.timestamp }
            .take(limit)

    /**
     * Adds the plays the desktop knows and this phone does not, and returns how
     * many were added (and kept: see [mergeHistory]).
     *
     * Two decisions live here.
     *
     * Not gated on [IncognitoMode], unlike [addPlayEvent]. Incognito stops the
     * phone from recording what is being played now; these are plays another
     * device already recorded, and dropping them would leave the two histories
     * disagreeing for a reason the user never chose. What incognito does stop is
     * the phone's own history going the other way (`DesktopSyncRepository`).
     *
     * Counted in the statistics, written straight to the history file and not
     * through [addPlayEvent]. Every statistic here (top songs, minutes, streaks,
     * monthly and daily series) is computed from the history file - there is no
     * separate counter - so an entry is either in the history and counted, or
     * absent. Counting is right: they are the same person's plays, and the
     * history screen and the stats screen read the same file, so entries the
     * stats ignored would make the two disagree. What would distort them is
     * [addPlayEvent] itself: it stamps "now" on the entry, so up to 3,000 old
     * plays would land on today (one huge day, a false streak, wrong months).
     * Here every entry keeps the date it was played, is deduplicated, and the
     * history never grows past [MAX_HISTORY_ENTRIES].
     *
     * The history has no deletion marker on the wire: a play removed on the
     * phone comes back from the desktop at the next sync.
     */
    suspend fun mergeSyncedHistory(entries: List<PlayHistoryEntry>): Int {
        if (entries.isEmpty()) return 0
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val merge = mergeHistory(loadHistory(), entries, MAX_HISTORY_ENTRIES, System.currentTimeMillis())
                if (merge.added == 0) return@withLock 0
                try {
                    historyFile.writeText(json.encodeToString(merge.entries))
                    merge.added
                } catch (e: Exception) {
                    // Nothing suspends inside this try, so no cancellation is swallowed.
                    KLog.e(TAG, "Error writing synced history", e)
                    0
                }
            }
        }
    }

    suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.Default) {
        val history = loadHistory()
        if (history.isEmpty()) return@withContext GlobalStats()
        val totalPlayTime = history.sumOf { it.duration / 1000 }
        val totalPlays = history.size
        val songStats = history.groupBy { it.songId }.map { (id, entries) ->
            val first = entries.first()
            SongStats(
                songId = id,
                title = first.title,
                artist = first.artist,
                thumbnailUrl = first.thumbnailUrl,
                playCount = entries.size,
                totalPlayTime = entries.sumOf { it.duration }
            )
        }.sortedByDescending { it.playCount }
        val artistStats = history.groupBy { it.artist }.map { (name, entries) ->
            ArtistStats(
                name = name,
                playCount = entries.size,
                songCount = entries.distinctBy { it.songId }.size
            )
        }.sortedByDescending { it.playCount }
        // Listening streaks: consecutive local-time days with at least one play.
        val playDays = history.map { localDayOf(it.timestamp) }.toSortedSet()
        var longestStreak = 0
        var run = 0
        var prevDay: Long? = null
        for (day in playDays) {
            run = if (prevDay != null && day == prevDay + 1) run + 1 else 1
            if (run > longestStreak) longestStreak = run
            prevDay = day
        }
        // Current streak stays alive if the last play was today OR yesterday
        val today = localDayOf(System.currentTimeMillis())
        var currentStreak = 0
        var cursor = if (today in playDays) today else today - 1
        while (cursor in playDays) {
            currentStreak++
            cursor--
        }

        GlobalStats(
            totalPlays = totalPlays,
            totalPlayTimeSeconds = totalPlayTime,
            topSongs = songStats.take(10),
            topArtists = artistStats.take(10),
            uniqueArtists = artistStats.size,
            uniqueSongs = songStats.size,
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak
        )
    }

    /** Epoch day in the device's local timezone. */
    private fun localDayOf(timestamp: Long): Long =
        (timestamp + java.util.TimeZone.getDefault().getOffset(timestamp)) / 86_400_000L

    suspend fun getMonthlyPlays(): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistory()
        val calendar = Calendar.getInstance()
        history.groupBy {
            calendar.timeInMillis = it.timestamp
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)
            "$year-${month.toString().padStart(2, '0')}"
        }.mapValues { it.value.size }
    }
    
    suspend fun getDailyPlays(daysLimit: Int = 7): Map<String, Int> = withContext(Dispatchers.Default) {
        val history = loadHistory()
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        history.filter { now - it.timestamp < daysLimit * 24 * 60 * 60 * 1000L }
            .groupBy {
                calendar.timeInMillis = it.timestamp
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val month = calendar.get(Calendar.MONTH) + 1
                "$month/$day"
            }.mapValues { it.value.size }
    }

    /**
     * Drop a single play.
     *
     * Identity is the song plus the instant it was played: entries carry no id
     * of their own, and [addPlayEvent] already debounces repeats of the same
     * song inside ten seconds, so the pair cannot collide in practice.
     *
     * Returns the surviving history so the caller can publish it without a
     * second read - the screen doing the removing is showing the list.
     */
    suspend fun removeEntry(songId: String, timestamp: Long): List<PlayHistoryEntry> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val remaining = loadHistory().filterNot {
                    it.songId == songId && it.timestamp == timestamp
                }
                writeHistory(remaining)
                remaining
            }
        }

    /**
     * Drop every play of one song. The list-level version of [removeEntry], for
     * a song that has been on repeat and owns half the log.
     */
    suspend fun removeAllPlaysOf(songId: String): List<PlayHistoryEntry> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val remaining = loadHistory().filterNot { it.songId == songId }
                writeHistory(remaining)
                remaining
            }
        }

    /**
     * Put entries back exactly as they were, newest first.
     *
     * Undo restores the whole list rather than re-inserting one entry, because
     * a removal can take a run of plays with it and the order has to come back
     * unchanged. Callers hold the pre-removal list; this only writes it.
     */
    suspend fun restoreHistory(entries: List<PlayHistoryEntry>) = withContext(Dispatchers.IO) {
        mutex.withLock { writeHistory(entries) }
    }

    /** Caller must hold [mutex]. */
    private fun writeHistory(entries: List<PlayHistoryEntry>) {
        try {
            if (entries.isEmpty()) {
                if (historyFile.exists()) historyFile.delete()
            } else {
                historyFile.writeText(json.encodeToString(entries))
            }
        } catch (e: Exception) {
            KLog.e(TAG, "Error writing history", e)
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        if (historyFile.exists()) historyFile.delete()
    }

    companion object {
        /** The ceiling the history is trimmed to, for a recorded play and for a synced one alike. */
        internal const val MAX_HISTORY_ENTRIES = 5000

        /** What the desktop keeps and so what is sent to it. */
        internal const val SYNC_HISTORY_LIMIT = 3000
    }
}

/** The result of [mergeHistory]: the new history, newest first, and how many received plays it holds. */
internal data class HistoryMerge(val entries: List<PlayHistoryEntry>, val added: Int)

/** Clock skew tolerated on a received play; past it the entry is dropped. */
private const val FUTURE_TOLERANCE_MS = 5 * 60_000L

/** One play is one song at one second: the same pair is the same play. */
internal fun playKey(entry: PlayHistoryEntry): Pair<String, Long> = entry.songId to entry.timestamp / 1000

/**
 * Merges plays received from another device into [existing] (newest first).
 * Pure, so the rules are testable without a device.
 *
 * - A received play whose [playKey] is already in [existing], or already seen
 *   earlier in [incoming], is not added: the phone's own plays come back from the
 *   desktop and must not double.
 * - A received play with no date, or dated later than [now] plus a few minutes,
 *   is dropped: a date of zero would land in 1970 in the monthly series, and a
 *   date in the future would sit at the head of the list and, through the
 *   debounce of `addPlayEvent`, swallow every replay of that song.
 * - The result is sorted newest first (the app reads it in that order, for
 *   "recently played" and the taste profile) and cut to [limit].
 * - [HistoryMerge.added] counts only received plays that survive the cut, so a
 *   full history does not report the same old plays as new on every sync.
 *
 * When nothing is added, [existing] is returned untouched.
 */
internal fun mergeHistory(
    existing: List<PlayHistoryEntry>,
    incoming: List<PlayHistoryEntry>,
    limit: Int,
    now: Long,
): HistoryMerge {
    val known = HashSet<Pair<String, Long>>(existing.size + incoming.size)
    for (entry in existing) known.add(playKey(entry))
    val fresh = ArrayList<PlayHistoryEntry>()
    for (entry in incoming) {
        if (entry.timestamp <= 0L || entry.timestamp > now + FUTURE_TOLERANCE_MS) continue
        if (known.add(playKey(entry))) fresh.add(entry)
    }
    if (fresh.isEmpty()) return HistoryMerge(existing, 0)
    val freshKeys = fresh.mapTo(HashSet()) { playKey(it) }
    // A stable sort: plays of the same instant keep the order they arrived in.
    val kept = (existing + fresh).sortedByDescending { it.timestamp }.take(limit)
    return HistoryMerge(kept, kept.count { playKey(it) in freshKeys })
}

data class GlobalStats(
    val totalPlays: Int = 0,
    val totalPlayTimeSeconds: Long = 0,
    val topSongs: List<SongStats> = emptyList(),
    val topArtists: List<ArtistStats> = emptyList(),
    val uniqueArtists: Int = 0,
    val uniqueSongs: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0
)
