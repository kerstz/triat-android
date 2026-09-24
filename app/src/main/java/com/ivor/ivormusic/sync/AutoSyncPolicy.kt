package com.ivor.ivormusic.sync

/**
 * When the automatic sync may run: the decision, kept apart from everything
 * Android so it can be held to its rules on the JVM. Time is an argument, never
 * read here, so a test spans minutes without waiting. [AutoSync] gathers the
 * inputs and acts on the answer.
 *
 * The rules, in one place:
 *  - never without a pairing, never off the local network (Wi-Fi or Ethernet,
 *    never mobile data), never while a sync is already running;
 *  - coming to the foreground syncs at most once every [FOREGROUND_MIN_GAP_MS];
 *  - a local edit syncs once nothing changed for [CHANGE_DEBOUNCE_MS];
 *  - an edit that a sync itself made (it applies playlists and likes) is not an
 *    edit: [isSyncEcho] drops it, or every sync would trigger the next one.
 */

/** At least this long between two syncs started by opening the app. */
internal const val FOREGROUND_MIN_GAP_MS = 5 * 60_000L

/** A local edit waits this long without another edit before it is synced. */
internal const val CHANGE_DEBOUNCE_MS = 30_000L

/**
 * Local changes seen while a sync runs, or this soon after it ended, are its
 * own writes arriving late (file watchers and flows report after the fact), not
 * something the user did.
 */
internal const val SELF_CHANGE_GRACE_MS = 5_000L

internal enum class SyncTrigger {
    /** The app came to the foreground. */
    FOREGROUND,

    /** Playlists or likes were edited on the phone. */
    LOCAL_CHANGE,
}

internal enum class SkipReason { NOT_PAIRED, NO_LOCAL_NETWORK, BUSY, TOO_SOON, NOTHING_PENDING }

internal sealed interface AutoSyncDecision {
    /** Start a sync now. */
    data object Run : AutoSyncDecision

    /** Not yet: ask again at [at] (epoch millis, the same clock as the inputs). */
    data class WaitUntil(val at: Long) : AutoSyncDecision

    /** Do nothing. A pending edit stays pending, and the next foreground picks it up. */
    data class Skip(val reason: SkipReason) : AutoSyncDecision
}

/**
 * Everything the decision looks at. Times are epoch millis; 0 in
 * [lastSyncEndedAt] means no sync yet. [pendingChangeAt] is the moment of the
 * latest local edit that no sync has carried yet, null when there is none.
 */
internal data class AutoSyncInputs(
    val now: Long,
    val paired: Boolean,
    val onLocalNetwork: Boolean,
    val busy: Boolean,
    val lastSyncEndedAt: Long,
    val pendingChangeAt: Long?,
)

internal fun decideAutoSync(trigger: SyncTrigger, i: AutoSyncInputs): AutoSyncDecision {
    if (!i.paired) return AutoSyncDecision.Skip(SkipReason.NOT_PAIRED)

    if (trigger == SyncTrigger.LOCAL_CHANGE) {
        val edited = i.pendingChangeAt ?: return AutoSyncDecision.Skip(SkipReason.NOTHING_PENDING)
        // The debounce comes before the network check: Wi-Fi may well be back
        // by the time it has run out. An edit dated in the future (the clock
        // moved back) counts as made now, so the wait is never longer than one debounce.
        val due = minOf(edited, i.now) + CHANGE_DEBOUNCE_MS
        if (i.now < due) return AutoSyncDecision.WaitUntil(due)
    }

    if (!i.onLocalNetwork) return AutoSyncDecision.Skip(SkipReason.NO_LOCAL_NETWORK)
    if (i.busy) return AutoSyncDecision.Skip(SkipReason.BUSY)

    return when (trigger) {
        SyncTrigger.LOCAL_CHANGE -> AutoSyncDecision.Run
        SyncTrigger.FOREGROUND -> when {
            // An edit that had to wait for the network (its debounce is long over) goes now.
            editIsDue(i) -> AutoSyncDecision.Run
            gapElapsed(i.now, i.lastSyncEndedAt, FOREGROUND_MIN_GAP_MS) -> AutoSyncDecision.Run
            else -> AutoSyncDecision.Skip(SkipReason.TOO_SOON)
        }
    }
}

private fun editIsDue(i: AutoSyncInputs): Boolean {
    val edited = i.pendingChangeAt ?: return false
    return i.now - edited >= CHANGE_DEBOUNCE_MS
}

/** Never synced, or long enough ago; a clock that went backwards counts as long ago. */
private fun gapElapsed(now: Long, last: Long, gap: Long): Boolean {
    if (last <= 0L) return true
    val elapsed = now - last
    return elapsed < 0L || elapsed >= gap
}

/**
 * True for a local change that is the echo of a sync rather than an edit by
 * the user: it arrived while a sync was running or within
 * [SELF_CHANGE_GRACE_MS] after one ended. [lastSyncEndedAt] is 0 before any sync.
 */
internal fun isSyncEcho(now: Long, busy: Boolean, lastSyncEndedAt: Long): Boolean {
    if (busy) return true
    if (lastSyncEndedAt <= 0L) return false
    val elapsed = now - lastSyncEndedAt
    return elapsed in 0L until SELF_CHANGE_GRACE_MS
}

/**
 * Whether a network with these transports may carry the sync: Wi-Fi or
 * Ethernet, and never a network that also rides mobile data (a VPN over
 * cellular reports both).
 */
internal fun isLocalTransport(wifi: Boolean, ethernet: Boolean, cellular: Boolean): Boolean =
    (wifi || ethernet) && !cellular
