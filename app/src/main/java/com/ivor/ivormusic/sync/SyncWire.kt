package com.ivor.ivormusic.sync

import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.UserPlaylist
import org.json.JSONArray
import org.json.JSONObject

/** A playlist received from the desktop, ready to be written locally. */
data class SyncedPlaylist(
    val id: String,
    val name: String,
    val songs: List<Song>,
    val updated: Long,
)

/** What the merged state from the desktop asks the phone to change. */
data class ApplyPlan(
    val playlistsToWrite: List<SyncedPlaylist>,
    val playlistsToDelete: List<String>,
    /** Track and the date of the like, in milliseconds. */
    val likesToAdd: List<Pair<Song, Long>>,
    /** Video id and the date of the unlike, in milliseconds (the author's, not now). */
    val likesToRemove: List<Pair<String, Long>>,
    val history: List<PlayHistoryEntry>,
)

/**
 * The exchange format of the Wi-Fi sync with Triat desktop (section 1.4 of
 * docs/sync.md, `triat/core/sync_state.py` on the desktop side).
 *
 * Pure functions over the app's models and `org.json`, so the whole format
 * is testable on the JVM. Timestamps are milliseconds, durations on the wire
 * are floating-point seconds. Only YouTube tracks (11-character ids) travel.
 */
object SyncWire {

    const val VERSION = 1
    private const val VIDEO_ID_LENGTH = 11

    /** The wire form of a track, or null when it is not a YouTube track. */
    fun trackToJson(song: Song): JSONObject? {
        if (song.id.length != VIDEO_ID_LENGTH || song.source == SongSource.LOCAL) return null
        return JSONObject()
            .put("id", song.id)
            .put("title", song.title)
            .put("artist", song.artist)
            .put("duration", song.duration / 1000.0)
            .put("thumb", song.thumbnailUrl ?: JSONObject.NULL)
    }

    /** A track received from the desktop, or null when it is not a YouTube video. */
    fun trackFromJson(o: JSONObject): Song? {
        if (o.isNull("id")) return null
        val id = o.optString("id", "")
        if (id.length != VIDEO_ID_LENGTH) return null
        return Song.fromYouTube(
            videoId = id,
            title = stringOrEmpty(o, "title"),
            artist = stringOrEmpty(o, "artist"),
            album = "",
            duration = (o.optDouble("duration", 0.0) * 1000).toLong(),
            thumbnailUrl = if (o.isNull("thumb")) null else o.optString("thumb", "").ifEmpty { null },
        )
    }

    /**
     * The full state of the phone. [tombstones] is keyed by kind
     * ([SyncTombstones.PLAYLIST], [SyncTombstones.LIKE]) and then by playlist
     * uid or video id, valued by the deletion date.
     */
    fun snapshot(
        playlists: List<UserPlaylist>,
        liked: List<Song>,
        tombstones: Map<String, Map<String, Long>>,
        history: List<PlayHistoryEntry>,
    ): JSONObject {
        val playlistArray = JSONArray()
        for (playlist in playlists) {
            val tracks = JSONArray()
            playlist.songs.mapNotNull { trackToJson(it) }.forEach { tracks.put(it) }
            playlistArray.put(
                JSONObject()
                    .put("uid", playlist.id)
                    .put("name", playlist.name)
                    .put("updated", effectiveUpdated(playlist))
                    .put("deleted", false)
                    .put("tracks", tracks)
            )
        }
        for ((uid, at) in tombstones[SyncTombstones.PLAYLIST].orEmpty()) {
            playlistArray.put(
                JSONObject()
                    .put("uid", uid)
                    .put("name", "")
                    .put("updated", at)
                    .put("deleted", true)
                    .put("tracks", JSONArray())
            )
        }

        val likeArray = JSONArray()
        for (song in liked) {
            val track = trackToJson(song) ?: continue
            likeArray.put(
                JSONObject()
                    .put("id", song.id)
                    .put("at", song.dateAdded ?: 0L)
                    .put("liked", true)
                    .put("track", track)
            )
        }
        for ((id, at) in tombstones[SyncTombstones.LIKE].orEmpty()) {
            likeArray.put(
                JSONObject()
                    .put("id", id)
                    .put("at", at)
                    .put("liked", false)
                    .put("track", JSONObject.NULL)
            )
        }

        val historyArray = JSONArray()
        for (entry in history) {
            if (entry.source != SongSource.YOUTUBE) continue
            val track = trackToJson(entry.toSong()) ?: continue
            historyArray.put(
                JSONObject()
                    .put("id", entry.songId)
                    .put("at", entry.timestamp)
                    // PlayHistoryEntry.duration is the song length in
                    // milliseconds (copied from Song.duration when the play
                    // is recorded), so /1000 gives the wire seconds.
                    .put("seconds", entry.duration / 1000.0)
                    .put("track", track)
            )
        }

        return JSONObject()
            .put("v", VERSION)
            .put("playlists", playlistArray)
            .put("likes", likeArray)
            .put("history", historyArray)
    }

    /**
     * What to change on the phone to match [merged], the state the desktop
     * built from its own data and the snapshot the phone sent.
     */
    fun plan(merged: JSONObject, local: List<UserPlaylist>, liked: List<Song>): ApplyPlan {
        val localById = local.associateBy { it.id }
        val likedById = liked.associateBy { it.id }

        val toWrite = mutableListOf<SyncedPlaylist>()
        val toDelete = mutableListOf<String>()
        val playlists = merged.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until playlists.length()) {
            val item = playlists.optJSONObject(i) ?: continue
            if (item.isNull("uid")) continue
            val uid = item.optString("uid", "")
            if (uid.isEmpty()) continue
            val updated = item.optLong("updated", 0L)
            val mine = localById[uid]
            if (item.optBoolean("deleted", false)) {
                if (mine != null && effectiveUpdated(mine) <= updated) toDelete += uid
            } else if (mine == null || effectiveUpdated(mine) < updated) {
                val tracks = item.optJSONArray("tracks") ?: JSONArray()
                val songs = (0 until tracks.length()).mapNotNull { index ->
                    tracks.optJSONObject(index)?.let { trackFromJson(it) }
                }
                toWrite += SyncedPlaylist(uid, stringOrEmpty(item, "name"), songs, updated)
            }
        }

        val toAdd = mutableListOf<Pair<Song, Long>>()
        val toRemove = mutableListOf<Pair<String, Long>>()
        val likes = merged.optJSONArray("likes") ?: JSONArray()
        for (i in 0 until likes.length()) {
            val item = likes.optJSONObject(i) ?: continue
            if (item.isNull("id")) continue
            val id = item.optString("id", "")
            if (id.isEmpty()) continue
            val at = item.optLong("at", 0L)
            val mine = likedById[id]
            if (item.optBoolean("liked", false)) {
                if (mine == null) {
                    val song = item.optJSONObject("track")?.let { trackFromJson(it) } ?: continue
                    toAdd += song to at
                }
            } else if (mine != null && (mine.dateAdded ?: 0L) <= at) {
                toRemove += id to at
            }
        }

        val received = mutableListOf<PlayHistoryEntry>()
        val history = merged.optJSONArray("history") ?: JSONArray()
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val song = item.optJSONObject("track")?.let { trackFromJson(it) } ?: continue
            received += PlayHistoryEntry(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                timestamp = item.optLong("at", 0L),
                // PlayHistoryEntry.duration is the length of the song (the
                // history rebuilds playable Songs from it, and the minutes
                // listened add it up per play). The desktop's "seconds" is the
                // time it was listened to, which can be a few seconds of a skip,
                // so the track's own length wins and "seconds" is the fallback.
                duration = if (song.duration > 0) song.duration else (item.optDouble("seconds", 0.0) * 1000).toLong(),
                thumbnailUrl = song.thumbnailUrl,
                source = SongSource.YOUTUBE,
            )
        }

        return ApplyPlan(toWrite, toDelete, toAdd, toRemove, received)
    }

    /**
     * The playlist a desktop copy produces on the phone: [incoming] (the
     * desktop only ever holds YouTube tracks) with the phone's own local songs
     * of [current] put back, each at its old index clamped to the list size.
     * Without this a mixed playlist lost its device songs on any desktop edit.
     */
    fun mergeLocalSongs(current: List<Song>, incoming: List<Song>): List<Song> {
        val merged = incoming.toMutableList()
        current.forEachIndexed { index, song ->
            if (!isSyncable(song)) merged.add(index.coerceAtMost(merged.size), song)
        }
        return merged
    }

    /** Whether a song travels at all: a YouTube id that is not a device file. */
    private fun isSyncable(song: Song): Boolean =
        song.id.length == VIDEO_ID_LENGTH && song.source != SongSource.LOCAL

    /** Playlists written before edit dates existed carry 0: fall back on creation. */
    private fun effectiveUpdated(playlist: UserPlaylist): Long =
        if (playlist.updatedAt > 0) playlist.updatedAt else playlist.createdAt

    /** org.json turns a JSON null into the string "null" through optString. */
    private fun stringOrEmpty(o: JSONObject, key: String): String =
        if (o.isNull(key)) "" else o.optString(key, "")

    private fun PlayHistoryEntry.toSong(): Song = Song(
        id = songId,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        thumbnailUrl = thumbnailUrl,
        source = source,
    )
}
