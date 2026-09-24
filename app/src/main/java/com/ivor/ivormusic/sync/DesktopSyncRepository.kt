package com.ivor.ivormusic.sync

import android.content.Context
import android.os.Build
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.IncognitoMode
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.PlaylistRepository
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.security.MessageDigest

/**
 * What the sync screen and the automatic sync draw from.
 *
 * [lastError] is a short code, never a sentence, for the UI to translate: one
 * of the `DesktopSyncClient.REASON_*` codes, or [DesktopSyncRepository.ERROR_UNPAIRED]
 * (the desktop forgot this phone) or [DesktopSyncRepository.ERROR_INTERNAL].
 * [lastChanges] is a short sentence in the app's language describing what the
 * last sync changed on the phone, null when it changed nothing (or before the
 * first sync of this run). [shareSession] mirrors the consent to receive the
 * desktop's YouTube session.
 */
data class SyncUiState(
    val paired: SyncStore.PairedDesktop? = null,
    val found: List<FoundDesktop> = emptyList(),
    val busy: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastError: String? = null,
    val lastChanges: String? = null,
    val shareSession: Boolean = false,
)

/**
 * The one entry point of the Wi-Fi sync with Triat desktop: pairing, the sync
 * itself, and what the UI shows about them. The Settings page and the
 * automatic sync both go through it.
 *
 * A process-wide singleton, listed in invariant 6 (docs/rules.md): a pairing
 * done in Settings must be visible to the automatic sync at once, and two
 * instances would each hold their own mutex and run two syncs at the same time.
 *
 * The desktop does the merging; this class sends the phone's snapshot
 * ([SyncWire.snapshot]), takes the merged state back and applies the difference
 * ([SyncWire.plan]). Only one sync runs at a time. Local Only mode does not
 * stop it: the exchange never leaves the local network ([DesktopSyncClient]
 * refuses any other host), and it is the user's own computer. Incognito does
 * hold back the listening history.
 */
class DesktopSyncRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val store = SyncStore(appContext)
    private val client = DesktopSyncClient()

    // Built on first use, from a background thread: their constructors read files.
    // The playlist repository keeps a per-instance cache, so it is reloaded from
    // disk before every read (other instances write the same files).
    private val playlists by lazy { PlaylistRepository(appContext) }
    private val likes by lazy { LikedSongsRepository(appContext) }
    private val stats by lazy { StatsRepository(appContext) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val mutableState = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = mutableState.asStateFlow()

    private val discoveryLock = Any()
    private var discoveryJob: Job? = null

    init {
        refreshFromStore()
    }

    // ---- discovery --------------------------------------------------------

    /**
     * Lists the desktops announcing themselves in [SyncUiState.found] for as
     * long as [scope] lives or until [stopDiscovery]. Calling it again
     * replaces the earlier collection.
     */
    fun startDiscovery(scope: CoroutineScope) {
        val job = scope.launch {
            DesktopDiscovery.discover(appContext).collect { list ->
                mutableState.update { it.copy(found = list) }
            }
        }
        val previous = synchronized(discoveryLock) { discoveryJob.also { discoveryJob = job } }
        previous?.cancel()
        job.invokeOnCompletion {
            // Only the current collection clears the list: a replaced one must
            // not wipe what its successor already found.
            synchronized(discoveryLock) {
                if (discoveryJob === job) {
                    discoveryJob = null
                    mutableState.update { s -> s.copy(found = emptyList()) }
                }
            }
        }
    }

    fun stopDiscovery() {
        synchronized(discoveryLock) { discoveryJob?.cancel() }
    }

    /** For the manual `ip:port` entry: who answers there, or null. */
    suspend fun lookup(host: String, port: Int): FoundDesktop? = client.hello(host, port)

    // ---- pairing ----------------------------------------------------------

    /**
     * Pairs with [found] using the code its screen shows. On success the
     * pairing is stored and a first sync starts in the background (its outcome
     * lands in [state]); the failures are returned for the UI to word.
     */
    suspend fun pair(found: FoundDesktop, code: String): PairResult {
        val deviceId = withContext(Dispatchers.IO) { store.deviceId }
        val result = client.pair(found.host, found.port, code, deviceId, deviceLabel(Build.MANUFACTURER, Build.MODEL))
        if (result is PairResult.Paired) {
            // Not while a sync of the previous pairing is in flight.
            mutex.withLock {
                store.save(result.desktop)
                mutableState.update { it.copy(lastChanges = null) }
                refreshFromStore()
            }
            // Its own scope: leaving the page must not cancel the first sync.
            scope.launch { syncNow() }
        }
        return result
    }

    /** Forgets the paired desktop (the phone stays on the desktop's list until it removes it). */
    fun forget() {
        store.forget()
        mutableState.update {
            it.copy(paired = null, lastSyncAt = 0L, lastError = null, lastChanges = null, shareSession = false)
        }
    }

    /**
     * The consent to receive the desktop's YouTube session. Changing it starts
     * over, so turning it back on applies the session again at the next sync.
     */
    fun setShareSessionAccepted(accepted: Boolean) {
        store.shareSessionAccepted = accepted
        store.lastSessionDigest = null
        mutableState.update { it.copy(shareSession = accepted) }
    }

    // ---- sync -------------------------------------------------------------

    /**
     * Syncs with the paired desktop now, waiting for a sync already running.
     * Returns `Failed(ERROR_NOT_PAIRED)` when there is no pairing. The result
     * is also recorded in [state]; an [SyncResult.Unauthorized] keeps the
     * pairing and sets [ERROR_UNPAIRED], the UI proposes to pair again.
     */
    suspend fun syncNow(): SyncResult = mutex.withLock {
        val desktop = store.paired() ?: return@withLock SyncResult.Failed(ERROR_NOT_PAIRED)
        mutableState.update { it.copy(busy = true) }
        try {
            conclude(desktop, exchange(desktop))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Reading or writing the local files failed; the network side reports its own.
            KLog.e(TAG, "Sync failed unexpectedly", e)
            record(desktop, ERROR_INTERNAL)
            SyncResult.Failed(ERROR_INTERNAL)
        } finally {
            mutableState.update { it.copy(busy = false) }
        }
    }

    /** Sends the snapshot; if the desktop is not where it was, finds it again and retries once. */
    private suspend fun exchange(desktop: SyncStore.PairedDesktop): SyncResult {
        val deviceId = withContext(Dispatchers.IO) { store.deviceId }
        val snapshot = withContext(Dispatchers.IO) { buildSnapshot() }
        val first = client.sync(desktop, deviceId, snapshot)
        if (first !is SyncResult.Failed || first.reason != DesktopSyncClient.REASON_NETWORK) return first

        val moved = rediscover(desktop) ?: return first
        store.updateAddress(moved.host, moved.port)
        refreshFromStore()
        val relocated = store.paired() ?: return first
        return client.sync(relocated, deviceId, snapshot)
    }

    /** The desktop at its new address, or null after a few seconds without finding it elsewhere. */
    private suspend fun rediscover(desktop: SyncStore.PairedDesktop): FoundDesktop? {
        movedTo(desktop, mutableState.value.found)?.let { return it }
        val seen = withTimeoutOrNull(REDISCOVERY_TIMEOUT_MS) {
            DesktopDiscovery.discover(appContext).first { movedTo(desktop, it) != null }
        } ?: return null
        return movedTo(desktop, seen)
    }

    private suspend fun buildSnapshot(): JSONObject {
        playlists.refreshPlaylists()
        val tombstones = SyncTombstones(appContext)
        return SyncWire.snapshot(
            playlists = playlists.userPlaylists.value,
            liked = likes.likedSongs.value,
            tombstones = mapOf(
                SyncTombstones.PLAYLIST to tombstones.all(SyncTombstones.PLAYLIST),
                SyncTombstones.LIKE to tombstones.all(SyncTombstones.LIKE),
            ),
            history = historyToSend(),
        )
    }

    /** Listening history is never sent from an incognito session; everything else still is. */
    private suspend fun historyToSend(): List<PlayHistoryEntry> {
        if (IncognitoMode.isEnabled(appContext)) return emptyList()
        return stats.historyForSync()
    }

    private suspend fun conclude(desktop: SyncStore.PairedDesktop, result: SyncResult): SyncResult {
        // Forgotten or paired with another desktop while the exchange was in flight:
        // the answer belongs to a pairing that no longer exists.
        if (store.paired()?.serverId != desktop.serverId) return SyncResult.Failed(ERROR_NOT_PAIRED)
        when (result) {
            is SyncResult.Ok -> {
                val changes = withContext(Dispatchers.IO) { apply(result.merged) }
                val now = System.currentTimeMillis()
                store.lastSyncAt = now
                store.lastError = null
                mutableState.update {
                    it.copy(paired = store.paired(), lastSyncAt = now, lastError = null, lastChanges = describe(changes))
                }
            }
            SyncResult.Unauthorized -> record(desktop, ERROR_UNPAIRED)
            is SyncResult.Failed -> record(desktop, result.reason)
        }
        return result
    }

    private fun record(desktop: SyncStore.PairedDesktop, error: String) {
        if (store.paired()?.serverId != desktop.serverId) return
        store.lastError = error
        mutableState.update { it.copy(lastError = error) }
    }

    /**
     * Applies what the desktop merged. The plan is made against what is on the
     * phone now, not against the snapshot that was sent: an edit made while the
     * exchange was running is newer than the desktop's copy and must win.
     */
    private suspend fun apply(merged: JSONObject): SyncChanges {
        playlists.refreshPlaylists()
        val plan = SyncWire.plan(merged, playlists.userPlaylists.value, likes.likedSongs.value)
        for (p in plan.playlistsToWrite) playlists.applySynced(p.id, p.name, p.songs, p.updated)
        for (id in plan.playlistsToDelete) playlists.deleteSynced(id)
        for ((song, at) in plan.likesToAdd) likes.likeSongAt(song, at)
        // Leaves a tombstone, on purpose: the desktop must hear about the unlike too.
        for (id in plan.likesToRemove) likes.unlikeSong(id)
        // The desktop's plays are another device's history: not held back by
        // IncognitoMode, which only stops this phone from recording (or sending)
        // its own. Deduplicated, capped and counted by StatsRepository.
        val historyAdded = stats.mergeSyncedHistory(plan.history)
        applySession(merged)
        return SyncChanges.of(plan, historyAdded)
    }

    /**
     * The YouTube session the desktop shares, if the user accepted it here.
     * Each application is a sign-in, so an unchanged session (same digest) is
     * left alone. It replaces the active account's cookies: that is what the
     * consent is for.
     */
    private fun applySession(merged: JSONObject) {
        if (!store.shareSessionAccepted) return
        val cookies = (merged.opt("cookies") as? String)?.trim().orEmpty()
        if (cookies.isEmpty()) return
        val digest = sessionDigest(cookies)
        if (digest == store.lastSessionDigest) return
        SessionManager(appContext).startSession(cookies)
        store.lastSessionDigest = digest
    }

    private fun describe(changes: SyncChanges): String? {
        val parts = changes.parts()
        if (parts.isEmpty()) return null
        val res = appContext.resources
        return parts.joinToString(", ") { (kind, n) ->
            val id = when (kind) {
                SyncChanges.Kind.PLAYLISTS_WRITTEN -> R.plurals.ds_changes_playlists_written
                SyncChanges.Kind.PLAYLISTS_DELETED -> R.plurals.ds_changes_playlists_deleted
                SyncChanges.Kind.LIKES_ADDED -> R.plurals.ds_changes_likes_added
                SyncChanges.Kind.LIKES_REMOVED -> R.plurals.ds_changes_likes_removed
                SyncChanges.Kind.HISTORY_ADDED -> R.plurals.ds_changes_history_added
            }
            res.getQuantityString(id, n, n)
        }
    }

    private fun refreshFromStore() {
        val paired = store.paired()
        mutableState.update {
            it.copy(
                paired = paired,
                lastSyncAt = if (paired == null) 0L else store.lastSyncAt,
                lastError = if (paired == null) null else store.lastError,
                shareSession = paired != null && store.shareSessionAccepted,
            )
        }
    }

    companion object {
        private const val TAG = "DesktopSyncRepository"

        /** The desktop forgot this phone (it answered 401): pair again. */
        const val ERROR_UNPAIRED = "unpaired"

        /** Something local broke while applying the answer. */
        const val ERROR_INTERNAL = "internal"

        /** Only returned by [syncNow], never stored: there was nothing to sync with. */
        const val ERROR_NOT_PAIRED = "not_paired"

        private const val REDISCOVERY_TIMEOUT_MS = 6_000L

        @Volatile private var instance: DesktopSyncRepository? = null

        fun get(context: Context): DesktopSyncRepository = instance ?: synchronized(this) {
            instance ?: DesktopSyncRepository(context.applicationContext).also { instance = it }
        }
    }
}

/**
 * What one sync changed on the phone, by kind. Pure, so the summary can be
 * tested without a device. [historyAdded] is what the history merge kept.
 */
internal data class SyncChanges(
    val playlistsWritten: Int = 0,
    val playlistsDeleted: Int = 0,
    val likesAdded: Int = 0,
    val likesRemoved: Int = 0,
    val historyAdded: Int = 0,
) {
    enum class Kind { PLAYLISTS_WRITTEN, PLAYLISTS_DELETED, LIKES_ADDED, LIKES_REMOVED, HISTORY_ADDED }

    val isEmpty: Boolean get() = parts().isEmpty()

    /** The kinds that changed, in a fixed order, each with its count; empty when nothing changed. */
    fun parts(): List<Pair<Kind, Int>> = listOf(
        Kind.PLAYLISTS_WRITTEN to playlistsWritten,
        Kind.PLAYLISTS_DELETED to playlistsDeleted,
        Kind.LIKES_ADDED to likesAdded,
        Kind.LIKES_REMOVED to likesRemoved,
        Kind.HISTORY_ADDED to historyAdded,
    ).filter { it.second > 0 }

    companion object {
        /**
         * [ApplyPlan.history] is deliberately not counted: it lists what was
         * received, not what was new. [historyAdded] is what the merge reports.
         */
        fun of(plan: ApplyPlan, historyAdded: Int = 0) = SyncChanges(
            playlistsWritten = plan.playlistsToWrite.size,
            playlistsDeleted = plan.playlistsToDelete.size,
            likesAdded = plan.likesToAdd.size,
            likesRemoved = plan.likesToRemove.size,
            historyAdded = historyAdded,
        )
    }
}

/**
 * Where the paired [desktop] can now be reached, if [seen] lists it (same
 * `serverId`) at an address other than the stored one. Null when it is not
 * listed, is listed where it already was (it is simply down), or the new
 * address is not a local one: nothing is ever saved that [isPrivateHost] refuses.
 */
internal fun movedTo(desktop: SyncStore.PairedDesktop, seen: List<FoundDesktop>): FoundDesktop? =
    seen.firstOrNull {
        it.serverId == desktop.serverId && isPrivateHost(it.host) &&
            (!it.host.equals(desktop.host, ignoreCase = true) || it.port != desktop.port)
    }

/** SHA-256 of a session's cookie header, in hex: enough to tell it changed, useless to replay. */
internal fun sessionDigest(cookies: String): String =
    MessageDigest.getInstance("SHA-256").digest(cookies.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/**
 * The name this phone is listed under on the desktop: the hardware
 * manufacturer and model, never a name the user typed (it may be theirs).
 */
internal fun deviceLabel(manufacturer: String?, model: String?): String {
    val brand = manufacturer.orEmpty().trim()
    val name = model.orEmpty().trim()
    val label = when {
        name.isEmpty() -> brand
        brand.isEmpty() || name.startsWith(brand, ignoreCase = true) -> name
        else -> "$brand $name"
    }
    return label.ifEmpty { "Android" }.replaceFirstChar { it.uppercase() }
}
