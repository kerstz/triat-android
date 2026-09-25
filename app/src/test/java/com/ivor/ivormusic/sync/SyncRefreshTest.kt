package com.ivor.ivormusic.sync

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a holder of its own playlist cache is told to reload.
 *
 * What is NOT tested here: [reloadAfterEachSync] itself, and the ViewModels
 * that call it. It needs the process-wide [DesktopSyncRepository] (a Context,
 * encrypted preferences) and a `PlaylistRepository` reading `filesDir`, neither
 * of which exists on the JVM; a synced playlist showing up in the Library
 * without a restart is checked by hand on a phone (task E1 of
 * docs/sync.md). The decision that matters is which state changes count
 * as a finished sync, and that is [finishedSyncs].
 */
class SyncRefreshTest {

    /** Runs [block] with the completions of [state] being collected; returns what was seen. */
    private fun collecting(
        state: MutableStateFlow<SyncUiState>,
        block: suspend () -> Unit,
    ): List<Long> = runBlocking {
        val seen = mutableListOf<Long>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            state.finishedSyncs().collect { seen += it }
        }
        block()
        yield()
        job.cancel()
        seen
    }

    private fun MutableStateFlow<SyncUiState>.setLastSyncAt(at: Long) {
        value = value.copy(lastSyncAt = at)
    }

    @Test
    fun `the sync recorded before collection started is not a completion`() {
        val state = MutableStateFlow(SyncUiState(lastSyncAt = 1_000L))
        assertTrue(collecting(state) { }.isEmpty())
    }

    @Test
    fun `a sync that finishes afterwards is reported with its time`() {
        val state = MutableStateFlow(SyncUiState(lastSyncAt = 1_000L))
        val seen = collecting(state) { state.setLastSyncAt(2_000L) }
        assertEquals(listOf(2_000L), seen)
    }

    @Test
    fun `the first sync ever, from a phone that never synced, counts`() {
        val state = MutableStateFlow(SyncUiState())
        val seen = collecting(state) { state.setLastSyncAt(5_000L) }
        assertEquals(listOf(5_000L), seen)
    }

    @Test
    fun `each sync is reported once`() {
        val state = MutableStateFlow(SyncUiState(lastSyncAt = 1_000L))
        val seen = collecting(state) {
            state.setLastSyncAt(2_000L)
            yield()
            state.setLastSyncAt(3_000L)
            yield()
        }
        assertEquals(listOf(2_000L, 3_000L), seen)
    }

    @Test
    fun `other changes to the state are not completions`() {
        val state = MutableStateFlow(SyncUiState(lastSyncAt = 1_000L))
        val seen = collecting(state) {
            state.value = state.value.copy(busy = true)
            yield()
            state.value = state.value.copy(lastError = "network")
            yield()
            state.value = state.value.copy(lastChanges = "1 playlist")
            yield()
            state.value = state.value.copy(busy = false)
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `forgetting the desktop is not a completion, nothing was written`() {
        val state = MutableStateFlow(SyncUiState(lastSyncAt = 1_000L))
        val seen = collecting(state) { state.setLastSyncAt(0L) }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `a sync after forgetting and pairing again is reported`() {
        val state = MutableStateFlow(SyncUiState(lastSyncAt = 1_000L))
        val seen = collecting(state) {
            state.setLastSyncAt(0L)
            yield()
            state.setLastSyncAt(9_000L)
        }
        assertEquals(listOf(9_000L), seen)
    }
}
