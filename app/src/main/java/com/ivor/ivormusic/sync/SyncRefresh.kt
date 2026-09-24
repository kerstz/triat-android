package com.ivor.ivormusic.sync

import android.content.Context
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lets a holder of its own cache find out that a Wi-Fi sync has written to disk.
 *
 * Every ViewModel builds its own `PlaylistRepository`, whose in-memory list is
 * read from disk when it is created and after its own writes. The sync writes
 * the playlists it receives through one more instance, so without this the
 * others keep showing what was there before, and worse, write their stale
 * copy back over the synced one on the next edit. Each holder therefore asks
 * to be told when a sync ends and reloads. This is deliberately not a shared
 * flow of playlists: invariant 6 (CLAUDE.md) closes the list of process-wide
 * repository state, and the sync repository is already on it, so its
 * [DesktopSyncRepository.state] is the signal and the disk stays the truth.
 *
 * Likes need none of this: `LikedSongsRepository` keeps its state at process
 * level, so the sync's writes reach every instance as they happen.
 */

/**
 * The completion time of each sync that finishes after collection starts.
 *
 * The value present at the start is dropped: it describes a sync that ended
 * before this holder existed, and the holder has just read the disk anyway.
 * [SyncUiState.lastSyncAt] is only set once a sync has applied everything, so
 * a change is a finished sync. A drop back to zero (the desktop was forgotten,
 * or a new pairing began) is not one and is skipped: nothing was written.
 */
internal fun Flow<SyncUiState>.finishedSyncs(): Flow<Long> =
    map { it.lastSyncAt }
        .distinctUntilChanged()
        .drop(1)
        .filter { it > 0L }

/**
 * Runs [reload] each time a Wi-Fi sync finishes, for as long as this scope lives.
 *
 * The sync repository is reached on the IO dispatcher: the first call builds
 * it, and that opens an encrypted preference file. A failure of [reload] is
 * logged and the next sync tries again; it must not end the collection, and in
 * a `viewModelScope` an escaping exception would take the app down.
 *
 * This is plumbing over a process-wide singleton and the disk, which need a
 * device, so it has no test of its own; what it decides is in [finishedSyncs].
 */
internal fun CoroutineScope.reloadAfterEachSync(context: Context, reload: suspend () -> Unit): Job =
    launch(Dispatchers.IO) {
        try {
            DesktopSyncRepository.get(context).state.finishedSyncs().collect {
                try {
                    reload()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    KLog.w("SyncRefresh", "Reloading after a sync failed", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w("SyncRefresh", "Could not follow the sync", e)
        }
    }
