package com.ivor.ivormusic.ui.settings

import com.ivor.ivormusic.sync.DesktopSyncClient
import com.ivor.ivormusic.sync.DesktopSyncRepository
import com.ivor.ivormusic.sync.PairResult
import com.ivor.ivormusic.sync.SyncStore
import com.ivor.ivormusic.sync.SyncUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure decisions of the desktop sync page, on invented data: what the code
 * field keeps, how a hand-typed address is read, how old a sync is called, and
 * which message each failure gets. The screen itself needs a device and is
 * checked by hand (task E1 of docs/sync.md).
 */
class DesktopSyncSettingsTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private val desktop = SyncStore.PairedDesktop(
        serverId = "abc123", name = "Triat - test-pc", host = "192.168.1.20", port = 47823,
        key = ByteArray(32) { it.toByte() },
    )

    // ---- the code field -----------------------------------------------------

    @Test
    fun `the code is upper case without dashes or spaces`() {
        assertEquals("ABCDEFGH", normalizeCodeInput("abcd-efgh"))
        assertEquals("K7QM2XPA", normalizeCodeInput(" k7qm 2xpa "))
        assertEquals("K7QM2XPA", normalizeCodeInput("K7QM-2XPA"))
        assertEquals("K7QM2XPA", normalizeCodeInput("k7qm 2xpa\n"))
    }

    @Test
    fun `the code is cut to eight characters`() {
        assertEquals("ABCDEFGH", normalizeCodeInput("ABCDEFGHJKMN"))
        assertEquals("ABCD", normalizeCodeInput("abcd"))
        assertEquals("", normalizeCodeInput(""))
    }

    @Test
    fun `characters that are not ASCII letters or digits are dropped`() {
        assertEquals("AB", normalizeCodeInput("é-ab"))
        assertEquals("AB12", normalizeCodeInput("a.b/1_2!"))
    }

    // ---- the manual address -------------------------------------------------

    @Test
    fun `an address with a port is read as typed`() {
        assertEquals(ManualAddress("192.168.1.20", 47823), parseManualAddress("192.168.1.20:47823"))
        assertEquals(ManualAddress("10.0.0.5", 5000), parseManualAddress("10.0.0.5:5000"))
        assertEquals(ManualAddress("test-pc.local", 5000), parseManualAddress("test-pc.local:5000"))
    }

    @Test
    fun `without a port the default one is used`() {
        assertEquals(ManualAddress("192.168.1.20", DEFAULT_SYNC_PORT), parseManualAddress("192.168.1.20"))
        assertEquals(ManualAddress("test-pc.local", DEFAULT_SYNC_PORT), parseManualAddress("test-pc.local"))
    }

    @Test
    fun `spaces, a scheme and a trailing slash are forgiven`() {
        assertEquals(ManualAddress("192.168.1.20", 47823), parseManualAddress("  192.168.1.20:47823 "))
        assertEquals(ManualAddress("192.168.1.20", 5000), parseManualAddress("http://192.168.1.20:5000/"))
        assertEquals(ManualAddress("192.168.1.20", 5000), parseManualAddress("HTTP://192.168.1.20:5000"))
    }

    @Test
    fun `IPv6 is read with or without brackets`() {
        assertEquals(ManualAddress("[fe80::1]", 5000), parseManualAddress("[fe80::1]:5000"))
        assertEquals(ManualAddress("[fe80::1]", DEFAULT_SYNC_PORT), parseManualAddress("[fe80::1]"))
        assertEquals(ManualAddress("[fe80::1]", DEFAULT_SYNC_PORT), parseManualAddress("fe80::1"))
    }

    @Test
    fun `an address outside the local network is refused before anything is sent`() {
        assertNull(parseManualAddress("8.8.8.8:47823"))
        assertNull(parseManualAddress("example.com"))
        assertNull(parseManualAddress("https://example.com:47823"))
        assertNull(parseManualAddress("localhost:47823"))
        assertNull(parseManualAddress("fe80::1%wlan0"))
    }

    @Test
    fun `text that is not an address is refused`() {
        for (bad in listOf(
            "", "   ", ":47823", "192.168.1.20:", "192.168.1.20:abc", "192.168.1.20:0", "192.168.1.20:65536",
            "192.168.1.20:123456", "192.168.1.20:-1", "192.168.1.20 :5000", "192.168.1.20/v1/hello",
            "user@192.168.1.20", "[fe80::1", "[fe80::1]x", "192.168.1.20:80:80x",
        )) {
            assertNull("'$bad' must be refused", parseManualAddress(bad))
        }
    }

    // ---- how old a sync is --------------------------------------------------

    @Test
    fun `a phone that never synced says so`() {
        assertEquals(SyncAge.Never, syncAge(0L, 1_700_000_000_000L))
    }

    @Test
    fun `under a minute reads as just now`() {
        val at = 1_700_000_000_000L
        assertEquals(SyncAge.JustNow, syncAge(at, at))
        assertEquals(SyncAge.JustNow, syncAge(at, at + minute - 1))
    }

    @Test
    fun `minutes, hours and days are rounded down`() {
        val at = 1_700_000_000_000L
        assertEquals(SyncAge.Minutes(1), syncAge(at, at + minute))
        assertEquals(SyncAge.Minutes(5), syncAge(at, at + 5 * minute + 59_000))
        assertEquals(SyncAge.Minutes(59), syncAge(at, at + hour - 1))
        assertEquals(SyncAge.Hours(1), syncAge(at, at + hour))
        assertEquals(SyncAge.Hours(23), syncAge(at, at + day - 1))
        assertEquals(SyncAge.Days(1), syncAge(at, at + day))
        assertEquals(SyncAge.Days(30), syncAge(at, at + 30 * day + hour))
    }

    @Test
    fun `a clock that went backwards reads as just now`() {
        val at = 1_700_000_000_000L
        assertEquals(SyncAge.JustNow, syncAge(at, at - 5 * minute))
    }

    // ---- which message ------------------------------------------------------

    @Test
    fun `each error code of the repository and the client has its own problem`() {
        assertEquals(SyncProblem.FORGOTTEN, problemFor(DesktopSyncRepository.ERROR_UNPAIRED))
        assertEquals(SyncProblem.UNREACHABLE, problemFor(DesktopSyncClient.REASON_NETWORK))
        assertEquals(SyncProblem.BAD_ADDRESS, problemFor(DesktopSyncClient.REASON_HOST))
        assertEquals(SyncProblem.BAD_ADDRESS, problemFor(DesktopSyncClient.REASON_PORT))
        assertEquals(SyncProblem.NOT_TRIAT, problemFor(DesktopSyncClient.REASON_SERVER))
        assertEquals(SyncProblem.NOT_TRIAT, problemFor(DesktopSyncClient.REASON_PROTOCOL))
        assertEquals(SyncProblem.UNVERIFIED, problemFor(DesktopSyncClient.REASON_PROOF))
        assertEquals(SyncProblem.INCOMPATIBLE, problemFor(DesktopSyncClient.REASON_VERSION))
        assertEquals(SyncProblem.DAMAGED_PAIRING, problemFor(DesktopSyncClient.REASON_KEY))
        assertEquals(SyncProblem.TOO_LARGE, problemFor(DesktopSyncClient.REASON_TOO_LARGE))
        assertEquals(SyncProblem.INTERNAL, problemFor(DesktopSyncRepository.ERROR_INTERNAL))
        assertEquals(SyncProblem.INTERNAL, problemFor(DesktopSyncClient.REASON_DEVICE))
    }

    @Test
    fun `an unknown code reads as an internal problem, never as raw text`() {
        assertEquals(SyncProblem.INTERNAL, problemFor("something_new"))
        assertEquals(SyncProblem.INTERNAL, problemFor(""))
        assertEquals(SyncProblem.INTERNAL, problemFor(DesktopSyncRepository.ERROR_NOT_PAIRED))
    }

    @Test
    fun `a wrong code and an expired code are told apart`() {
        assertEquals(SyncProblem.WRONG_CODE, problemFor(PairResult.WrongCode))
        assertEquals(SyncProblem.CODE_EXPIRED, problemFor(PairResult.PairingClosed))
        assertNotEquals(problemFor(PairResult.WrongCode), problemFor(PairResult.PairingClosed))
    }

    @Test
    fun `a failed pairing goes through the same table, a good one has no problem`() {
        assertEquals(SyncProblem.UNREACHABLE, problemFor(PairResult.Failed(DesktopSyncClient.REASON_NETWORK)))
        assertEquals(SyncProblem.UNVERIFIED, problemFor(PairResult.Failed(DesktopSyncClient.REASON_PROOF)))
        assertNull(problemFor(PairResult.Paired(desktop)))
    }

    @Test
    fun `only a forgotten or damaged pairing offers to pair again`() {
        val offering = SyncProblem.entries.filter { it.needsNewPairing }
        assertEquals(setOf(SyncProblem.FORGOTTEN, SyncProblem.DAMAGED_PAIRING), offering.toSet())
        assertFalse(SyncProblem.UNREACHABLE.needsNewPairing)
        assertTrue(SyncProblem.FORGOTTEN.needsNewPairing)
    }

    // ---- the one-line status ------------------------------------------------

    private val now = 1_700_000_000_000L

    @Test
    fun `no pairing reads as not paired whatever else is set`() {
        assertEquals(SyncStatus.NotPaired, syncStatus(SyncUiState(), now))
        assertEquals(SyncStatus.NotPaired, syncStatus(SyncUiState(busy = true, lastError = "network"), now))
    }

    @Test
    fun `a running sync wins over an old error`() {
        val state = SyncUiState(paired = desktop, busy = true, lastError = "network", lastSyncAt = now - hour)
        assertEquals(SyncStatus.Syncing, syncStatus(state, now))
    }

    @Test
    fun `an error is shown until a sync works again`() {
        val state = SyncUiState(paired = desktop, lastError = DesktopSyncRepository.ERROR_UNPAIRED, lastSyncAt = now - hour)
        assertEquals(SyncStatus.Problem(SyncProblem.FORGOTTEN), syncStatus(state, now))
    }

    @Test
    fun `a healthy pairing reports the age of the last sync`() {
        assertEquals(
            SyncStatus.Synced(SyncAge.Minutes(5)),
            syncStatus(SyncUiState(paired = desktop, lastSyncAt = now - 5 * minute), now),
        )
        assertEquals(SyncStatus.Synced(SyncAge.Never), syncStatus(SyncUiState(paired = desktop), now))
    }
}
