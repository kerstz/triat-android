package com.ivor.ivormusic.sync

import android.app.Activity
import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.FileObserver
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Syncs with the paired Triat desktop without being asked: when the app comes to
 * the foreground, and a little after playlists or likes were edited on the
 * phone. Never on mobile data, never two at once, never in a loop. What decides
 * is [decideAutoSync]; this class only gathers its inputs and acts on it.
 *
 * Started once, from `IvorMusicApplication.onCreate`; it lives as long as the
 * process. It owns no repository state (the pairing and the sync mutex belong
 * to [DesktopSyncRepository]): it watches, decides, and calls `syncNow()`.
 *
 * Foreground is "the first activity started", the same meaning as the
 * lifecycle library's process owner, counted here because that library is not
 * a direct dependency of the app. A rotation stops and restarts the activity,
 * so it looks like a new foreground; the minimum gap absorbs it.
 *
 * Local edits are noticed where they land. Likes live in process-wide flows.
 * Playlists do not: each `PlaylistRepository` keeps its own cache of the
 * `playlists/` directory, and one created here would never hear of the edits
 * the ViewModels make through theirs. So the directory itself is watched, which
 * every instance's writes and deletes go through.
 *
 * All the mutable state below is confined to [scope], one thread.
 */
class AutoSync(
    private val app: Application,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    /** Completed once the repositories are open; a foreground that arrives earlier waits for it. */
    private val ready = CompletableDeferred<DesktopSyncRepository>()

    private var started = false
    private var startedActivities = 0

    // Confined to [scope] from here on.
    private var repository: DesktopSyncRepository? = null
    private var pendingChangeAt: Long? = null
    private var debounceJob: Job? = null
    private var ownSync: Job? = null
    private var sawBusy = false
    private var observedEndAt = 0L

    /** Idempotent; cheap on the calling thread (everything else happens in the background). */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        app.registerActivityLifecycleCallbacks(foregroundCallbacks)
        scope.launch {
            try {
                watch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best effort: without this the sync stays manual, which still works.
                KLog.e(TAG, "Automatic sync could not start", e)
                // A foreground waiting for the repositories must not wait for ever.
                ready.cancel()
            }
        }
    }

    // ---- wiring -------------------------------------------------------------

    private suspend fun watch() {
        // Both constructors read files (and the pairing is in encrypted preferences).
        val repo = withContext(Dispatchers.IO) { DesktopSyncRepository.get(app) }
        val likes = withContext(Dispatchers.IO) { LikedSongsRepository(app) }
        repository = repo
        ready.complete(repo)
        coroutineScope {
            launch { repo.state.collect { onSyncState(it) } }
            // The first value is what is already stored, not an edit.
            launch { likes.likedSongIds.drop(1).collect { onLocalChange() } }
            launch { likes.likedSongs.drop(1).collect { onLocalChange() } }
            launch { playlistFileChanges(File(app.filesDir, PLAYLISTS_DIR)).collect { onLocalChange() } }
        }
    }

    private val foregroundCallbacks = object : Application.ActivityLifecycleCallbacks {
        // Main thread only, like every lifecycle callback.
        override fun onActivityStarted(activity: Activity) {
            if (++startedActivities == 1) scope.launch { onForeground() }
        }

        override fun onActivityStopped(activity: Activity) {
            if (startedActivities > 0) startedActivities--
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    // ---- events (all on [scope]) --------------------------------------------

    private suspend fun onForeground() {
        ready.await()
        if (decideAutoSync(SyncTrigger.FOREGROUND, inputs()) == AutoSyncDecision.Run) startSync()
    }

    private fun onLocalChange() {
        val now = clock()
        val state = repository?.state?.value ?: return
        // Read at arrival, from the repository's own state: it stamps lastSyncAt
        // before it lowers busy, so a write of a sync is always seen as one.
        val echo = isSyncEcho(now, state.busy || ownSync?.isActive == true, maxOf(state.lastSyncAt, observedEndAt))
        if (echo || state.paired == null) return
        pendingChangeAt = now
        scheduleChangeSync()
    }

    /** Also sees the syncs the Settings page starts: whatever was waiting is in their snapshot. */
    private fun onSyncState(state: SyncUiState) {
        if (state.busy) {
            sawBusy = true
            pendingChangeAt = null
            debounceJob?.cancel()
        } else if (sawBusy) {
            sawBusy = false
            observedEndAt = clock()
        }
    }

    private fun scheduleChangeSync() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            while (true) {
                when (val decision = decideAutoSync(SyncTrigger.LOCAL_CHANGE, inputs())) {
                    AutoSyncDecision.Run -> {
                        startSync()
                        return@launch
                    }
                    is AutoSyncDecision.WaitUntil -> delay((decision.at - clock()).coerceAtLeast(1L))
                    is AutoSyncDecision.Skip -> return@launch
                }
            }
        }
    }

    /**
     * In its own job, so that [onSyncState] cancelling a pending debounce never
     * cancels the sync itself.
     */
    private fun startSync() {
        val repo = repository ?: return
        pendingChangeAt = null
        ownSync = scope.launch {
            try {
                repo.syncNow()
            } finally {
                observedEndAt = clock()
            }
        }
    }

    private fun inputs(): AutoSyncInputs {
        val state = repository?.state?.value
        return AutoSyncInputs(
            now = clock(),
            paired = state?.paired != null,
            onLocalNetwork = onLocalNetwork(),
            busy = state?.busy == true || ownSync?.isActive == true,
            lastSyncEndedAt = maxOf(state?.lastSyncAt ?: 0L, observedEndAt),
            pendingChangeAt = pendingChangeAt,
        )
    }

    // ---- Android ------------------------------------------------------------

    /** The active network is Wi-Fi or Ethernet, and is not also mobile data. */
    private fun onLocalNetwork(): Boolean = try {
        val manager = app.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        capabilities != null && isLocalTransport(
            wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            ethernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        )
    } catch (e: SecurityException) {
        false
    }

    /** One item per burst of writes to, or deletions from, the playlist files. */
    private fun playlistFileChanges(dir: File): Flow<Unit> = callbackFlow {
        dir.mkdirs()
        val observer = object : FileObserver(
            dir,
            FileObserver.CLOSE_WRITE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM,
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && path.endsWith(".json")) trySend(Unit)
            }
        }
        observer.startWatching()
        awaitClose { observer.stopWatching() }
    }.conflate()

    private companion object {
        const val TAG = "AutoSync"

        /** Where `PlaylistRepository` keeps one JSON file per playlist, under `filesDir`. */
        const val PLAYLISTS_DIR = "playlists"
    }
}
