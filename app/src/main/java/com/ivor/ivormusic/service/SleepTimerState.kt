package com.ivor.ivormusic.service

import android.content.Context

/**
 * What the service persists about the sleep timer so it survives the media
 * service being recreated (process death, a swiped-away app).
 *
 * Kept in its own small preferences file rather than in ThemePreferences: it is
 * transient playback state, not a setting, and it is excluded from backup so a
 * restored phone never wakes up with a stale wall-clock deadline.
 */
internal class SleepTimerStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SleepTimerSnapshot = SleepTimerSnapshot(
        endsAt = prefs.getLong(KEY_ENDS_AT, 0L),
        endOfTrack = prefs.getBoolean(KEY_END_OF_TRACK, false),
    )

    fun save(endsAt: Long, endOfTrack: Boolean) {
        prefs.edit().putLong(KEY_ENDS_AT, endsAt).putBoolean(KEY_END_OF_TRACK, endOfTrack).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_ENDS_AT).remove(KEY_END_OF_TRACK).apply()
    }

    companion object {
        const val PREFS_NAME = "triat_sleep_timer"
        private const val KEY_ENDS_AT = "ends_at"
        private const val KEY_END_OF_TRACK = "end_of_track"
    }
}

internal data class SleepTimerSnapshot(val endsAt: Long, val endOfTrack: Boolean)

/** Pure rules of the sleep timer, kept out of the service so they can be tested. */
internal object SleepTimerRules {
    /** The volume ramps down over this long before the timer pauses playback. */
    const val FADE_MS = 30_000L

    /** What a service that starts up should do with the stored timer. */
    sealed interface Restore {
        data object None : Restore
        data object EndOfTrack : Restore
        data class Duration(val endsAt: Long) : Restore
    }

    fun restore(snapshot: SleepTimerSnapshot, nowMs: Long): Restore = when {
        snapshot.endOfTrack -> Restore.EndOfTrack
        snapshot.endsAt > nowMs -> Restore.Duration(snapshot.endsAt)
        else -> Restore.None
    }

    /**
     * Volume multiplier for [remainingMs] left on the timer: 1 until the last
     * [FADE_MS], then a linear ramp to 0 at the deadline. Squared so the
     * perceived loudness falls more evenly than the raw amplitude would.
     */
    fun fadeGain(remainingMs: Long): Float {
        if (remainingMs >= FADE_MS) return 1f
        val linear = (remainingMs.coerceAtLeast(0L).toFloat() / FADE_MS)
        return linear * linear
    }
}
