package com.ivor.ivormusic.sync

import com.ivor.ivormusic.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure parts of the sync repository, on invented data.
 *
 * What is NOT tested here: the orchestration itself (`syncNow`, `pair`,
 * `startDiscovery`, applying a plan through the playlist and likes
 * repositories, the session hand-over). It needs a Context, encrypted
 * preferences, NsdManager and a desktop on the network, none of which exist on
 * the JVM; it is checked by hand on a phone and a desktop (task E1 of
 * docs/sync.md). The decisions it takes are pulled out into the
 * functions below so that they can be held to their rules here.
 */
class DesktopSyncRepositoryTest {

    private val desktop = SyncStore.PairedDesktop(
        serverId = "abc123", name = "Triat - test-pc", host = "192.168.1.20", port = 47823,
        key = ByteArray(32) { it.toByte() },
    )

    private fun found(id: String = "abc123", host: String = "192.168.1.20", port: Int = 47823) =
        FoundDesktop(id, "Triat - test-pc", host, port)

    private fun song(id: String) = Song.fromYouTube(id, "Some Song", "Some Artist", "", 183_000L, null)

    // ---- movedTo ------------------------------------------------------------

    @Test
    fun `the same desktop at another address is where to look now`() {
        val moved = found(host = "192.168.1.55")
        assertEquals(moved, movedTo(desktop, listOf(moved)))
    }

    @Test
    fun `the same desktop on another port counts as moved`() {
        val moved = found(port = 50000)
        assertEquals(moved, movedTo(desktop, listOf(moved)))
    }

    @Test
    fun `a desktop listed where it already was has not moved`() {
        assertNull(movedTo(desktop, listOf(found())))
        assertNull(movedTo(desktop.copy(host = "[FE80::1]"), listOf(found(host = "[fe80::1]"))))
    }

    @Test
    fun `another desktop is never taken for this one`() {
        assertNull(movedTo(desktop, listOf(found(id = "other", host = "192.168.1.55"))))
    }

    @Test
    fun `nothing is listed, nothing moved`() {
        assertNull(movedTo(desktop, emptyList()))
    }

    @Test
    fun `an address off the local network is never adopted`() {
        // 203.0.113.0/24 is reserved for documentation: public, not private.
        assertNull(movedTo(desktop, listOf(found(host = "203.0.113.5"))))
        // A refused entry does not hide a good one behind it.
        val good = found(host = "10.0.0.7")
        assertEquals(good, movedTo(desktop, listOf(found(host = "203.0.113.5"), good)))
    }

    @Test
    fun `an ipv6 link-local address is adopted, bracketed as discovery gives it`() {
        val moved = found(host = "[fe80::1234]")
        assertEquals(moved, movedTo(desktop, listOf(moved)))
    }

    // ---- SyncChanges --------------------------------------------------------

    private fun plan(
        write: Int = 0, delete: Int = 0, add: Int = 0, remove: Int = 0, received: Int = 0,
    ) = ApplyPlan(
        playlistsToWrite = List(write) { SyncedPlaylist("p$it", "Mix", emptyList(), 1L) },
        playlistsToDelete = List(delete) { "d$it" },
        likesToAdd = List(add) { song("aaaaaaaaaa$it") to 1L },
        likesToRemove = List(remove) { "r$it" to 1L },
        history = List(received) {
            com.ivor.ivormusic.data.PlayHistoryEntry("aaaaaaaaaa$it", "T", "A", "", 1L, 1000L)
        },
    )

    @Test
    fun `a plan that changes nothing is an empty summary`() {
        val changes = SyncChanges.of(plan())
        assertTrue(changes.isEmpty)
        assertTrue(changes.parts().isEmpty())
    }

    @Test
    fun `the summary counts each kind`() {
        val changes = SyncChanges.of(plan(write = 2, delete = 1, add = 3, remove = 4))
        assertEquals(SyncChanges(2, 1, 3, 4, 0), changes)
        assertFalse(changes.isEmpty)
    }

    @Test
    fun `the summary lists only what changed, in a fixed order`() {
        val parts = SyncChanges.of(plan(delete = 1, add = 3)).parts()
        assertEquals(
            listOf(SyncChanges.Kind.PLAYLISTS_DELETED to 1, SyncChanges.Kind.LIKES_ADDED to 3),
            parts,
        )
    }

    @Test
    fun `received history is not counted as changed until it is merged`() {
        assertTrue(SyncChanges.of(plan(received = 40)).isEmpty)
        assertEquals(
            listOf(SyncChanges.Kind.HISTORY_ADDED to 12),
            SyncChanges.of(plan(received = 40), historyAdded = 12).parts(),
        )
    }

    // ---- sessionDigest ------------------------------------------------------

    @Test
    fun `the session digest is stable, changes with the cookies and hides them`() {
        val cookies = "SID=aaaa; HSID=bbbb"
        val digest = sessionDigest(cookies)
        assertEquals(digest, sessionDigest(cookies))
        assertNotEquals(digest, sessionDigest("SID=aaaa; HSID=cccc"))
        assertEquals(64, digest.length)
        assertTrue(digest.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(digest.contains("aaaa"))
    }

    @Test
    fun `the session digest is the store's memory of what was applied`() {
        val store = SyncStore(com.ivor.ivormusic.data.VolatileSharedPreferences())
        store.save(desktop)
        val digest = sessionDigest("SID=aaaa")
        assertNull(store.lastSessionDigest)
        store.lastSessionDigest = digest
        assertEquals(digest, store.lastSessionDigest)
        // Pairing again, or forgetting, starts from nothing received.
        store.save(desktop.copy(serverId = "other"))
        assertNull(store.lastSessionDigest)
        store.lastSessionDigest = digest
        store.forget()
        assertNull(store.lastSessionDigest)
    }

    // ---- deviceLabel --------------------------------------------------------

    @Test
    fun `the device label joins the maker and the model once`() {
        assertEquals("Google Pixel 8", deviceLabel("Google", "Pixel 8"))
        assertEquals("Samsung SM-S911B", deviceLabel("samsung", "SM-S911B"))
        // Many models already start with the brand.
        assertEquals("Xiaomi 13", deviceLabel("Xiaomi", "Xiaomi 13"))
    }

    @Test
    fun `the device label always has something in it`() {
        assertEquals("Pixel 8", deviceLabel(null, "Pixel 8"))
        assertEquals("Google", deviceLabel("Google", ""))
        assertEquals("Android", deviceLabel(null, null))
        assertEquals("Android", deviceLabel("  ", "  "))
    }

    // ---- clock skew ---------------------------------------------------------

    @Test
    fun `a small skew or no server clock is not a problem`() {
        assertFalse(clockProblem(1_000_000L, null))
        assertFalse(clockProblem(1_000_000L, 1_000_000L + 120_000L))
        assertFalse(clockProblem(1_000_000L, 1_000_000L - 120_000L))
    }

    @Test
    fun `more than two minutes either way is a problem`() {
        assertTrue(clockProblem(1_000_000L, 1_000_000L + 120_001L))
        assertTrue(clockProblem(1_000_000L, 1_000_000L - 120_001L))
    }

    @Test
    fun `the server clock is read from a reply only when present`() {
        assertEquals(1_700_000_000_000L, serverNow(org.json.JSONObject().put("now", 1_700_000_000_000L)))
        assertNull(serverNow(org.json.JSONObject()))
    }
}
