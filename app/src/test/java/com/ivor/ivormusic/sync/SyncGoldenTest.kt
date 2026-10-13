package com.ivor.ivormusic.sync

import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.UserPlaylist
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The v1 sync golden shared with Triat desktop (`tests/fixtures/
 * sync_v1_snapshot.json` there, read by `tests/test_sync_server.py`). The
 * blobs were sealed by the desktop; this checks the phone opens them, builds
 * the same snapshot from the same library, and applies the merged state the
 * way the desktop expects. Change the file on both sides or not at all.
 */
class SyncGoldenTest {

    private val fx: JSONObject = JSONObject(
        checkNotNull(javaClass.getResource("/sync_v1_snapshot.json")).readText()
    )
    private val key = hex(fx.getString("key_hex"))
    private val deviceId = fx.getString("device_id")
    private val requestNonce = fx.getString("request_n")
    private val snapshot = fx.getJSONObject("snapshot")
    private val merged = fx.getJSONObject("merged")

    private fun blob(name: String) = SyncCrypto.unb64(fx.getJSONObject("blobs").getString(name))

    // ---- crypto --------------------------------------------------------

    @Test
    fun `the desktop's legacy request opens to the snapshot`() {
        assertJsonEquals(snapshot, JSONObject(SyncCrypto.open(key, blob("legacy_request"), deviceId)))
    }

    @Test
    fun `the desktop's legacy response opens to the merged state`() {
        assertJsonEquals(merged, JSONObject(SyncCrypto.open(key, blob("legacy_response"), deviceId)))
    }

    /**
     * The phone still speaks the legacy framing (device id alone as
     * associated data), which the desktop's `/v1/sync` accepts as a fallback.
     * A direction-bound blob must not open under it, or a request could be
     * replayed as a response.
     */
    @Test
    fun `direction-bound blobs do not open under the legacy framing`() {
        for (name in listOf("bound_request", "bound_response")) {
            try {
                SyncCrypto.open(key, blob(name), deviceId)
                fail("$name opened with the legacy associated data")
            } catch (expected: Exception) {
            }
        }
    }

    /** Same primitives, desktop's `request_aad` / `response_aad`. */
    @Test
    fun `direction-bound blobs open with the desktop's associated data`() {
        val request = JSONObject(openWithAad(blob("bound_request"), "$deviceId|req"))
        val expected = JSONObject(snapshot.toString())
            .put("ts", fx.getLong("request_ts"))
            .put("n", requestNonce)
        assertJsonEquals(expected, request)

        val response = JSONObject(openWithAad(blob("bound_response"), "$deviceId|resp|$requestNonce"))
        assertJsonEquals(merged, response)

        // A request read as a response (reflection) is refused.
        try {
            openWithAad(blob("bound_request"), "$deviceId|resp|$requestNonce")
            fail("a request opened as a response")
        } catch (expected: Exception) {
        }
    }

    @Test
    fun `the phone's own sealing round-trips the snapshot`() {
        val sealed = SyncCrypto.seal(key, snapshot.toString(), deviceId)
        assertJsonEquals(snapshot, JSONObject(SyncCrypto.open(key, sealed, deviceId)))
    }

    // ---- wire format ---------------------------------------------------

    private fun track(id: String, title: String, artist: String, durationMs: Long) =
        Song.fromYouTube(id, title, artist, "", durationMs, null)

    private val one = track("dQw4w9WgXcQ", "Titre un", "Artiste A", 212_000L)
    private val two = track("9bZkp7q19f0", "Titre deux", "Artiste B", 253_500L)

    /**
     * The library the golden describes, as the phone holds it. The phone's
     * history only knows song lengths, so its "seconds" is the track length
     * where the golden (written from the desktop's side) carries listened
     * time: that one field is compared separately.
     */
    @Test
    fun `the phone builds the golden snapshot from the same library`() {
        val built = SyncWire.snapshot(
            playlists = listOf(
                UserPlaylist(id = "pl-1", name = "Route", createdAt = 1L,
                    songs = listOf(one, two), updatedAt = 1_700_000_000_000L),
            ),
            liked = listOf(one.copy(dateAdded = 1_700_000_200_000L)),
            tombstones = mapOf(
                SyncTombstones.PLAYLIST to mapOf("pl-gone" to 1_700_000_100_000L),
                SyncTombstones.LIKE to mapOf("kJQP7kiw5Fk" to 1_700_000_300_000L),
            ),
            history = listOf(
                PlayHistoryEntry("9bZkp7q19f0", "Titre deux", "Artiste B", "",
                    1_700_000_400_000L, 253_500L, null, SongSource.YOUTUBE),
                PlayHistoryEntry("dQw4w9WgXcQ", "Titre un", "Artiste A", "",
                    1_700_000_500_000L, 212_000L, null, SongSource.YOUTUBE),
            ),
        )

        val history = built.getJSONArray("history")
        assertEquals(253.5, history.getJSONObject(0).getDouble("seconds"), 0.0)
        assertEquals(212.0, history.getJSONObject(1).getDouble("seconds"), 0.0)

        assertJsonEquals(withoutSeconds(snapshot), withoutSeconds(built))
    }

    @Test
    fun `the merged state applies to an empty phone`() {
        val plan = SyncWire.plan(merged, local = emptyList(), liked = emptyList())

        assertEquals(1, plan.playlistsToWrite.size)
        val route = plan.playlistsToWrite.single()
        assertEquals("pl-1", route.id)
        assertEquals("Route", route.name)
        assertEquals(1_700_000_000_000L, route.updated)
        assertEquals(listOf("dQw4w9WgXcQ", "9bZkp7q19f0"), route.songs.map { it.id })
        assertEquals(listOf("Titre un", "Titre deux"), route.songs.map { it.title })
        assertEquals(listOf("Artiste A", "Artiste B"), route.songs.map { it.artist })
        assertEquals(listOf(212_000L, 253_500L), route.songs.map { it.duration })

        // Nothing local to delete or unlike.
        assertTrue(plan.playlistsToDelete.isEmpty())
        assertTrue(plan.likesToRemove.isEmpty())

        assertEquals(listOf("dQw4w9WgXcQ" to 1_700_000_200_000L),
            plan.likesToAdd.map { (song, at) -> song.id to at })

        // History keeps the desktop's order; durations are track lengths
        // (the desktop's 0.0 "seconds" means unknown, not a zero-length song).
        assertEquals(listOf("dQw4w9WgXcQ", "9bZkp7q19f0"), plan.history.map { it.songId })
        assertEquals(listOf(1_700_000_500_000L, 1_700_000_400_000L), plan.history.map { it.timestamp })
        assertEquals(listOf(212_000L, 253_500L), plan.history.map { it.duration })
        assertTrue(plan.history.all { it.source == SongSource.YOUTUBE })
    }

    @Test
    fun `the merged state's deletions reach older local copies only`() {
        val older = listOf(
            UserPlaylist(id = "pl-gone", name = "Old", createdAt = 1L, updatedAt = 1_700_000_099_999L),
            UserPlaylist(id = "pl-1", name = "Route", createdAt = 1L,
                songs = listOf(one, two), updatedAt = 1_700_000_000_000L),
        )
        val likedBefore = listOf(
            track("kJQP7kiw5Fk", "Trois", "C", 1_000L).copy(dateAdded = 1_700_000_299_999L),
            one.copy(dateAdded = 1_700_000_200_000L),
        )
        val plan = SyncWire.plan(merged, older, likedBefore)
        assertEquals(listOf("pl-gone"), plan.playlistsToDelete)
        assertTrue("pl-1 is already up to date", plan.playlistsToWrite.isEmpty())
        assertEquals(listOf("kJQP7kiw5Fk" to 1_700_000_300_000L), plan.likesToRemove)
        assertTrue(plan.likesToAdd.isEmpty())

        // Edited on the phone after the deletion: the phone's copy wins.
        val newer = listOf(UserPlaylist(id = "pl-gone", name = "Kept", createdAt = 1L,
            updatedAt = 1_700_000_100_001L))
        assertFalse("pl-gone" in SyncWire.plan(merged, newer, emptyList()).playlistsToDelete)
    }

    // ---- helpers -------------------------------------------------------

    private fun openWithAad(blob: ByteArray, aad: String): String {
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            updateAAD(aad.toByteArray())
            doFinal(blob.copyOfRange(12, blob.size))
        }
        return String(GZIPInputStream(ByteArrayInputStream(plain)).use { it.readBytes() })
    }

    private fun withoutSeconds(o: JSONObject): JSONObject {
        val copy = JSONObject(o.toString())
        val history = copy.getJSONArray("history")
        for (i in 0 until history.length()) history.getJSONObject(i).remove("seconds")
        return copy
    }

    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** JSON equality where 212 and 212.0 are the same number, as in Python. */
    private fun assertJsonEquals(expected: Any?, actual: Any?, path: String = "$") {
        when {
            expected is JSONObject && actual is JSONObject -> {
                val keys = expected.keys().asSequence().toSet()
                assertEquals("keys at $path", keys, actual.keys().asSequence().toSet())
                for (k in keys) assertJsonEquals(expected.get(k), actual.get(k), "$path.$k")
            }
            expected is JSONArray && actual is JSONArray -> {
                assertEquals("length at $path", expected.length(), actual.length())
                for (i in 0 until expected.length()) assertJsonEquals(expected.get(i), actual.get(i), "$path[$i]")
            }
            expected is Number && actual is Number ->
                assertEquals("number at $path", 0,
                    BigDecimal(expected.toString()).compareTo(BigDecimal(actual.toString())))
            else -> assertEquals("value at $path", expected, actual)
        }
    }
}
