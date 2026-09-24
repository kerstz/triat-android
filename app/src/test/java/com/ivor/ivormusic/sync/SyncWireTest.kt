package com.ivor.ivormusic.sync

import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.UserPlaylist
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exchange format of the desktop sync, on invented data. The wire shapes
 * are the ones `triat/core/sync_state.py` produces and consumes.
 */
class SyncWireTest {

    private fun youtube(id: String, title: String = "Some Song", durationMs: Long = 183_000L, thumb: String? = "https://example.invalid/t.jpg") =
        Song.fromYouTube(id, title, "Some Artist", "", durationMs, thumb)

    private fun local(id: String = "12345678901") = Song(
        id = id, title = "Local", artist = "Someone", album = "Album",
        duration = 1000L, source = SongSource.LOCAL,
    )

    private fun playlist(id: String, updatedAt: Long, createdAt: Long = 1L, songs: List<Song> = emptyList()) =
        UserPlaylist(id = id, name = "Mix $id", createdAt = createdAt, songs = songs, updatedAt = updatedAt)

    private fun merged(
        playlists: List<JSONObject> = emptyList(),
        likes: List<JSONObject> = emptyList(),
        history: List<JSONObject> = emptyList(),
    ) = JSONObject()
        .put("v", 1)
        .put("playlists", JSONArray(playlists))
        .put("likes", JSONArray(likes))
        .put("history", JSONArray(history))

    private fun wirePlaylist(uid: String, updated: Long, deleted: Boolean = false, tracks: List<JSONObject> = emptyList()) =
        JSONObject().put("uid", uid).put("name", "Mix $uid").put("updated", updated)
            .put("deleted", deleted).put("tracks", JSONArray(tracks))

    private fun wireLike(id: String, at: Long, liked: Boolean, track: JSONObject?) =
        JSONObject().put("id", id).put("at", at).put("liked", liked).put("track", track ?: JSONObject.NULL)

    private fun wireTrack(id: String) =
        JSONObject().put("id", id).put("title", "Some Song").put("artist", "Some Artist")
            .put("duration", 183.0).put("thumb", JSONObject.NULL)

    @Test
    fun `a local song is not exported`() {
        assertNull(SyncWire.trackToJson(local()))
        // Even with an id of the right length, the source decides.
        assertNull(SyncWire.trackToJson(youtube("aaaaaaaaaaa").copy(source = SongSource.LOCAL)))
    }

    @Test
    fun `an id of 10 characters is refused both ways`() {
        assertNull(SyncWire.trackToJson(youtube("aaaaaaaaaa")))
        assertNull(SyncWire.trackFromJson(wireTrack("aaaaaaaaaa")))
        assertNull(SyncWire.trackFromJson(JSONObject().put("title", "No id")))
        assertNull(SyncWire.trackFromJson(wireTrack("aaaaaaaaaaaa")))
    }

    @Test
    fun `a track round-trips, milliseconds on the phone and seconds on the wire`() {
        val song = youtube("aaaaaaaaaaa", durationMs = 183_500L)
        val json = SyncWire.trackToJson(song)!!
        assertEquals("aaaaaaaaaaa", json.getString("id"))
        assertEquals(183.5, json.getDouble("duration"), 0.0001)
        assertEquals("https://example.invalid/t.jpg", json.getString("thumb"))

        val back = SyncWire.trackFromJson(json)!!
        assertEquals(song.id, back.id)
        assertEquals(song.title, back.title)
        assertEquals(song.artist, back.artist)
        assertEquals(183_500L, back.duration)
        assertEquals(song.thumbnailUrl, back.thumbnailUrl)
        assertEquals(SongSource.YOUTUBE, back.source)
    }

    @Test
    fun `a missing thumbnail is a JSON null and comes back as null`() {
        val json = SyncWire.trackToJson(youtube("aaaaaaaaaaa", thumb = null))!!
        assertTrue(json.has("thumb"))
        assertTrue(json.isNull("thumb"))
        assertNull(SyncWire.trackFromJson(json)!!.thumbnailUrl)
    }

    @Test
    fun `the snapshot carries playlists, deletions, likes and history`() {
        val songs = listOf(youtube("aaaaaaaaaaa"), local(), youtube("bbbbbbbbbbb"))
        val snapshot = SyncWire.snapshot(
            playlists = listOf(
                playlist("p1", updatedAt = 5_000L, songs = songs),
                playlist("p2", updatedAt = 0L, createdAt = 4_000L),
            ),
            liked = listOf(youtube("ccccccccccc").copy(dateAdded = 9_000L), local()),
            tombstones = mapOf(
                SyncTombstones.PLAYLIST to mapOf("gone" to 7_000L),
                SyncTombstones.LIKE to mapOf("ddddddddddd" to 8_000L),
            ),
            history = listOf(
                PlayHistoryEntry("eeeeeeeeeee", "Heard", "Some Artist", "", 6_000L, 200_000L, null, SongSource.YOUTUBE),
                PlayHistoryEntry("12345678901", "Local", "Someone", "", 6_500L, 1_000L, null, SongSource.LOCAL),
            ),
        )
        assertEquals(1, snapshot.getInt("v"))

        val playlists = snapshot.getJSONArray("playlists")
        assertEquals(3, playlists.length())
        val first = playlists.getJSONObject(0)
        assertEquals("p1", first.getString("uid"))
        assertEquals(5_000L, first.getLong("updated"))
        assertFalse(first.getBoolean("deleted"))
        // The local file is left out, the two YouTube tracks stay.
        assertEquals(2, first.getJSONArray("tracks").length())
        // Never edited since the sync existed: creation date stands in.
        assertEquals(4_000L, playlists.getJSONObject(1).getLong("updated"))
        val deleted = playlists.getJSONObject(2)
        assertEquals("gone", deleted.getString("uid"))
        assertTrue(deleted.getBoolean("deleted"))
        assertEquals(7_000L, deleted.getLong("updated"))
        assertEquals(0, deleted.getJSONArray("tracks").length())

        val likes = snapshot.getJSONArray("likes")
        assertEquals(2, likes.length())
        assertEquals("ccccccccccc", likes.getJSONObject(0).getString("id"))
        assertEquals(9_000L, likes.getJSONObject(0).getLong("at"))
        assertTrue(likes.getJSONObject(0).getBoolean("liked"))
        assertFalse(likes.getJSONObject(1).getBoolean("liked"))
        assertTrue(likes.getJSONObject(1).isNull("track"))

        val history = snapshot.getJSONArray("history")
        assertEquals(1, history.length())
        assertEquals("eeeeeeeeeee", history.getJSONObject(0).getString("id"))
        assertEquals(6_000L, history.getJSONObject(0).getLong("at"))
        assertEquals(200.0, history.getJSONObject(0).getDouble("seconds"), 0.0001)
        assertEquals("eeeeeeeeeee", history.getJSONObject(0).getJSONObject("track").getString("id"))
    }

    @Test
    fun `a playlist deleted on the desktop is removed when it is not newer here`() {
        val plan = SyncWire.plan(
            merged(playlists = listOf(wirePlaylist("p1", updated = 5_000L, deleted = true))),
            local = listOf(playlist("p1", updatedAt = 4_000L)),
            liked = emptyList(),
        )
        assertEquals(listOf("p1"), plan.playlistsToDelete)
        assertTrue(plan.playlistsToWrite.isEmpty())
    }

    @Test
    fun `a playlist edited here after the deletion is kept`() {
        val plan = SyncWire.plan(
            merged(playlists = listOf(wirePlaylist("p1", updated = 5_000L, deleted = true))),
            local = listOf(playlist("p1", updatedAt = 6_000L)),
            liked = emptyList(),
        )
        assertTrue(plan.playlistsToDelete.isEmpty())
        assertTrue(plan.playlistsToWrite.isEmpty())
    }

    @Test
    fun `a deletion of a playlist that is not here is ignored`() {
        val plan = SyncWire.plan(
            merged(playlists = listOf(wirePlaylist("p9", updated = 5_000L, deleted = true))),
            local = emptyList(),
            liked = emptyList(),
        )
        assertTrue(plan.playlistsToDelete.isEmpty())
        assertTrue(plan.playlistsToWrite.isEmpty())
    }

    @Test
    fun `a newer or unknown playlist is written, an older or equal one is not`() {
        val tracks = listOf(wireTrack("aaaaaaaaaaa"), wireTrack("bad"))
        val plan = SyncWire.plan(
            merged(
                playlists = listOf(
                    wirePlaylist("new", updated = 5_000L, tracks = tracks),
                    wirePlaylist("newer", updated = 5_000L),
                    wirePlaylist("older", updated = 3_000L),
                    wirePlaylist("same", updated = 4_000L),
                )
            ),
            local = listOf(
                playlist("newer", updatedAt = 4_000L),
                playlist("older", updatedAt = 4_000L),
                playlist("same", updatedAt = 4_000L),
            ),
            liked = emptyList(),
        )
        assertEquals(listOf("new", "newer"), plan.playlistsToWrite.map { it.id })
        val written = plan.playlistsToWrite.first()
        assertEquals("Mix new", written.name)
        assertEquals(5_000L, written.updated)
        // The entry that is not a YouTube video is dropped.
        assertEquals(listOf("aaaaaaaaaaa"), written.songs.map { it.id })
        assertTrue(plan.playlistsToDelete.isEmpty())
    }

    @Test
    fun `a playlist that was never dated is compared on its creation date`() {
        val plan = SyncWire.plan(
            merged(playlists = listOf(wirePlaylist("p1", updated = 4_000L))),
            local = listOf(playlist("p1", updatedAt = 0L, createdAt = 4_000L)),
            liked = emptyList(),
        )
        assertTrue(plan.playlistsToWrite.isEmpty())
    }

    @Test
    fun `a like unknown here is added with its date`() {
        val plan = SyncWire.plan(
            merged(likes = listOf(wireLike("aaaaaaaaaaa", 5_000L, true, wireTrack("aaaaaaaaaaa")))),
            local = emptyList(),
            liked = emptyList(),
        )
        assertEquals(1, plan.likesToAdd.size)
        assertEquals("aaaaaaaaaaa", plan.likesToAdd[0].first.id)
        assertEquals(5_000L, plan.likesToAdd[0].second)
        assertTrue(plan.likesToRemove.isEmpty())
    }

    @Test
    fun `a like already here is not added again`() {
        val plan = SyncWire.plan(
            merged(likes = listOf(wireLike("aaaaaaaaaaa", 5_000L, true, wireTrack("aaaaaaaaaaa")))),
            local = emptyList(),
            liked = listOf(youtube("aaaaaaaaaaa").copy(dateAdded = 5_000L)),
        )
        assertTrue(plan.likesToAdd.isEmpty())
        assertTrue(plan.likesToRemove.isEmpty())
    }

    @Test
    fun `a like removed on the desktop is removed here unless liked again later`() {
        val removal = wireLike("aaaaaaaaaaa", 5_000L, false, null)
        val older = SyncWire.plan(
            merged(likes = listOf(removal)),
            local = emptyList(),
            liked = listOf(youtube("aaaaaaaaaaa").copy(dateAdded = 4_000L)),
        )
        assertEquals(listOf("aaaaaaaaaaa"), older.likesToRemove)
        assertTrue(older.likesToAdd.isEmpty())

        val newer = SyncWire.plan(
            merged(likes = listOf(removal)),
            local = emptyList(),
            liked = listOf(youtube("aaaaaaaaaaa").copy(dateAdded = 6_000L)),
        )
        assertTrue(newer.likesToRemove.isEmpty())

        val absent = SyncWire.plan(merged(likes = listOf(removal)), emptyList(), emptyList())
        assertTrue(absent.likesToRemove.isEmpty())
    }

    @Test
    fun `received history is passed on whole, with seconds turned back into milliseconds`() {
        val entry = JSONObject().put("id", "aaaaaaaaaaa").put("at", 5_000L)
            .put("seconds", 183.0).put("track", wireTrack("aaaaaaaaaaa"))
        val notATrack = JSONObject().put("id", "short").put("at", 6_000L).put("seconds", 10.0)
            .put("track", wireTrack("short"))
        val plan = SyncWire.plan(merged(history = listOf(entry, entry, notATrack)), emptyList(), emptyList())

        // No de-duplication here: that belongs to the history store.
        assertEquals(2, plan.history.size)
        val received = plan.history[0]
        assertEquals("aaaaaaaaaaa", received.songId)
        assertEquals(5_000L, received.timestamp)
        assertEquals(183_000L, received.duration)
        assertEquals(SongSource.YOUTUBE, received.source)
        assertNotNull(received.title)
    }

    @Test
    fun `an empty or partial state changes nothing`() {
        val plan = SyncWire.plan(JSONObject().put("v", 1), emptyList(), emptyList())
        assertTrue(plan.playlistsToWrite.isEmpty())
        assertTrue(plan.playlistsToDelete.isEmpty())
        assertTrue(plan.likesToAdd.isEmpty())
        assertTrue(plan.likesToRemove.isEmpty())
        assertTrue(plan.history.isEmpty())
    }
}
