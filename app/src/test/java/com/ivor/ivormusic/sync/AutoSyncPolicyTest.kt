package com.ivor.ivormusic.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The automatic sync's decision, on a clock the test moves by hand. The wiring
 * around it (lifecycle callbacks, file watching, the network query, the delays)
 * needs a device and is checked by hand (task E1 of docs/TRIAT-TASKS.md).
 */
class AutoSyncPolicyTest {

    private val minute = 60_000L
    private val second = 1_000L
    private val t0 = 1_700_000_000_000L

    private fun inputs(
        now: Long = t0,
        paired: Boolean = true,
        onLocalNetwork: Boolean = true,
        busy: Boolean = false,
        lastSyncEndedAt: Long = 0L,
        pendingChangeAt: Long? = null,
    ) = AutoSyncInputs(now, paired, onLocalNetwork, busy, lastSyncEndedAt, pendingChangeAt)

    private fun skip(reason: SkipReason) = AutoSyncDecision.Skip(reason)

    // ---- foreground ---------------------------------------------------------

    @Test
    fun `foreground with no sync yet runs`() {
        assertEquals(AutoSyncDecision.Run, decideAutoSync(SyncTrigger.FOREGROUND, inputs()))
    }

    @Test
    fun `foreground within five minutes of the last sync is too soon`() {
        val last = t0 - 4 * minute - 59 * second
        assertEquals(skip(SkipReason.TOO_SOON), decideAutoSync(SyncTrigger.FOREGROUND, inputs(lastSyncEndedAt = last)))
    }

    @Test
    fun `foreground exactly five minutes after the last sync runs`() {
        val last = t0 - FOREGROUND_MIN_GAP_MS
        assertEquals(AutoSyncDecision.Run, decideAutoSync(SyncTrigger.FOREGROUND, inputs(lastSyncEndedAt = last)))
    }

    @Test
    fun `a clock that went backwards does not block the foreground sync`() {
        val last = t0 + 3 * 60 * minute
        assertEquals(AutoSyncDecision.Run, decideAutoSync(SyncTrigger.FOREGROUND, inputs(lastSyncEndedAt = last)))
    }

    @Test
    fun `foreground waits for nothing but does run for an edit that waited for the network`() {
        val recent = t0 - minute
        // The last sync was a minute ago: too soon on its own...
        assertEquals(
            skip(SkipReason.TOO_SOON),
            decideAutoSync(SyncTrigger.FOREGROUND, inputs(lastSyncEndedAt = recent)),
        )
        // ...but an edit whose debounce is long over is waiting to go.
        assertEquals(
            AutoSyncDecision.Run,
            decideAutoSync(SyncTrigger.FOREGROUND, inputs(lastSyncEndedAt = recent, pendingChangeAt = t0 - 2 * minute)),
        )
        // An edit still inside its debounce does not force it: its own timer will.
        assertEquals(
            skip(SkipReason.TOO_SOON),
            decideAutoSync(SyncTrigger.FOREGROUND, inputs(lastSyncEndedAt = recent, pendingChangeAt = t0 - 5 * second)),
        )
    }

    // ---- gates common to both triggers -------------------------------------

    @Test
    fun `not paired never syncs`() {
        for (trigger in SyncTrigger.values()) {
            assertEquals(
                skip(SkipReason.NOT_PAIRED),
                decideAutoSync(trigger, inputs(paired = false, pendingChangeAt = t0 - minute)),
            )
        }
    }

    @Test
    fun `off the local network never syncs`() {
        assertEquals(
            skip(SkipReason.NO_LOCAL_NETWORK),
            decideAutoSync(SyncTrigger.FOREGROUND, inputs(onLocalNetwork = false)),
        )
        assertEquals(
            skip(SkipReason.NO_LOCAL_NETWORK),
            decideAutoSync(SyncTrigger.LOCAL_CHANGE, inputs(onLocalNetwork = false, pendingChangeAt = t0 - minute)),
        )
    }

    @Test
    fun `never while a sync is running`() {
        assertEquals(skip(SkipReason.BUSY), decideAutoSync(SyncTrigger.FOREGROUND, inputs(busy = true)))
        assertEquals(
            skip(SkipReason.BUSY),
            decideAutoSync(SyncTrigger.LOCAL_CHANGE, inputs(busy = true, pendingChangeAt = t0 - minute)),
        )
    }

    // ---- local change -------------------------------------------------------

    @Test
    fun `a change with nothing pending has nothing to do`() {
        assertEquals(skip(SkipReason.NOTHING_PENDING), decideAutoSync(SyncTrigger.LOCAL_CHANGE, inputs()))
    }

    @Test
    fun `a fresh change waits out the debounce`() {
        val decision = decideAutoSync(SyncTrigger.LOCAL_CHANGE, inputs(pendingChangeAt = t0 - 10 * second))
        assertEquals(AutoSyncDecision.WaitUntil(t0 + 20 * second), decision)
    }

    @Test
    fun `the debounce is measured from the latest change and runs out exactly at thirty seconds`() {
        val edited = t0
        assertEquals(
            AutoSyncDecision.WaitUntil(edited + CHANGE_DEBOUNCE_MS),
            decideAutoSync(SyncTrigger.LOCAL_CHANGE, inputs(now = edited + CHANGE_DEBOUNCE_MS - 1, pendingChangeAt = edited)),
        )
        assertEquals(
            AutoSyncDecision.Run,
            decideAutoSync(SyncTrigger.LOCAL_CHANGE, inputs(now = edited + CHANGE_DEBOUNCE_MS, pendingChangeAt = edited)),
        )
    }

    @Test
    fun `the debounce is not the five minute gap`() {
        // A sync ended ten seconds ago; a quiet edit still goes 30 s after it was made.
        val decision = decideAutoSync(
            SyncTrigger.LOCAL_CHANGE,
            inputs(lastSyncEndedAt = t0 - 10 * second, pendingChangeAt = t0 - CHANGE_DEBOUNCE_MS),
        )
        assertEquals(AutoSyncDecision.Run, decision)
    }

    @Test
    fun `the debounce is waited out before the network is judged`() {
        val decision = decideAutoSync(
            SyncTrigger.LOCAL_CHANGE,
            inputs(onLocalNetwork = false, pendingChangeAt = t0 - 5 * second),
        )
        assertEquals(AutoSyncDecision.WaitUntil(t0 + 25 * second), decision)
    }

    @Test
    fun `an edit dated in the future never waits longer than one debounce`() {
        val decision = decideAutoSync(SyncTrigger.LOCAL_CHANGE, inputs(pendingChangeAt = t0 + 3 * 60 * minute))
        assertEquals(AutoSyncDecision.WaitUntil(t0 + CHANGE_DEBOUNCE_MS), decision)
    }

    // ---- the echo of a sync -------------------------------------------------

    @Test
    fun `a change while a sync runs is its echo`() {
        assertTrue(isSyncEcho(t0, busy = true, lastSyncEndedAt = 0L))
        assertTrue(isSyncEcho(t0, busy = true, lastSyncEndedAt = t0 - 10 * minute))
    }

    @Test
    fun `a change within five seconds after a sync is its echo`() {
        val ended = t0
        assertTrue(isSyncEcho(ended, busy = false, lastSyncEndedAt = ended))
        assertTrue(isSyncEcho(ended + SELF_CHANGE_GRACE_MS - 1, busy = false, lastSyncEndedAt = ended))
    }

    @Test
    fun `a change five seconds or more after a sync is the user`() {
        val ended = t0
        assertFalse(isSyncEcho(ended + SELF_CHANGE_GRACE_MS, busy = false, lastSyncEndedAt = ended))
        assertFalse(isSyncEcho(ended + minute, busy = false, lastSyncEndedAt = ended))
    }

    @Test
    fun `with no sync yet a change is the user`() {
        assertFalse(isSyncEcho(t0, busy = false, lastSyncEndedAt = 0L))
    }

    /**
     * The loop this rule exists to break: a sync applies playlists, the writes
     * are reported late, and each report would schedule the next sync. Played
     * out on a clock: the writes land while busy and just after, then nothing
     * is pending, so nothing is scheduled.
     */
    @Test
    fun `a sync's own writes never schedule another sync`() {
        val started = t0
        val ended = t0 + 2 * second
        val writes = listOf(started + 500, ended - 1, ended, ended + 300, ended + 4 * second)
        var pending: Long? = null
        for (at in writes) {
            val busy = at < ended
            if (!isSyncEcho(at, busy, lastSyncEndedAt = if (busy) 0L else ended)) pending = at
        }
        assertEquals(null, pending)
        val decision = decideAutoSync(
            SyncTrigger.LOCAL_CHANGE,
            inputs(now = ended + 40 * second, lastSyncEndedAt = ended, pendingChangeAt = pending),
        )
        assertEquals(skip(SkipReason.NOTHING_PENDING), decision)
    }

    // ---- network ------------------------------------------------------------

    @Test
    fun `wifi and ethernet are local, mobile data is not`() {
        assertTrue(isLocalTransport(wifi = true, ethernet = false, cellular = false))
        assertTrue(isLocalTransport(wifi = false, ethernet = true, cellular = false))
        assertFalse(isLocalTransport(wifi = false, ethernet = false, cellular = true))
        assertFalse(isLocalTransport(wifi = false, ethernet = false, cellular = false))
    }

    @Test
    fun `a network that also rides mobile data is not local`() {
        assertFalse(isLocalTransport(wifi = true, ethernet = false, cellular = true))
        assertFalse(isLocalTransport(wifi = false, ethernet = true, cellular = true))
    }
}
