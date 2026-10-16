package com.ivor.ivormusic.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ivor.ivormusic.data.VolatileSharedPreferences
import com.ivor.ivormusic.util.KLog
import java.util.Base64
import java.util.UUID

/**
 * What the phone remembers about the desktop it is paired with: the desktop's
 * identity and address, the shared key that seals every exchange, and the
 * state of the last sync.
 *
 * The key is a credential, so everything lives in an encrypted preference file
 * (`triat_sync_pairing`, the same scheme as the Last.fm store) and that file is
 * excluded from every backup: Android cloud backup and device transfer
 * (`backup_rules.xml`, `data_extraction_rules.xml`), and Koda's own backup,
 * whose allowlist does not name it. Restoring it elsewhere would either leave
 * an unreadable ciphertext (the encryption key is bound to the device) or
 * clone the pairing onto a second phone. A restored phone pairs again.
 *
 * The device id is generated once and kept for the life of the install, also
 * across [forget], so the desktop can recognise the phone when it pairs again.
 */
class SyncStore internal constructor(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(openPrivatePrefs(context.applicationContext))

    /**
     * A desktop this phone has paired with. [key] is the 32-byte shared secret;
     * arrays compare by identity in Kotlin, so equality is spelled out, and
     * [toString] leaves the key out so it cannot reach a log by accident.
     */
    data class PairedDesktop(
        val serverId: String,
        val name: String,
        val host: String,
        val port: Int,
        val key: ByteArray,
        /** The desktop answered in the direction-bound framing at least once: never fall back (anti-downgrade). */
        val boundConfirmed: Boolean = false,
    ) {
        override fun equals(other: Any?): Boolean =
            other is PairedDesktop && serverId == other.serverId && name == other.name &&
                host == other.host && port == other.port && key.contentEquals(other.key) &&
                boundConfirmed == other.boundConfirmed

        override fun hashCode(): Int {
            var result = serverId.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + host.hashCode()
            result = 31 * result + port
            result = 31 * result + key.contentHashCode()
            return result
        }

        override fun toString(): String = "PairedDesktop(serverId=$serverId, name=$name, host=$host, port=$port)"
    }

    /** Stable random id of this phone, sent as `device_id` and bound into the sealed payloads. */
    val deviceId: String by lazy {
        synchronized(this) {
            prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also {
                    // commit: an id that is used to pair must never be lost to a killed process.
                    prefs.edit().putString(KEY_DEVICE_ID, it).commit()
                }
        }
    }

    /** The paired desktop, or null when none is stored or the stored record is unusable. */
    fun paired(): PairedDesktop? = synchronized(this) {
        val serverId = prefs.getString(KEY_SERVER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val host = prefs.getString(KEY_HOST, null)?.takeIf { it.isNotBlank() } ?: return null
        val port = prefs.getInt(KEY_PORT, 0).takeIf { it in 1..65535 } ?: return null
        val key = prefs.getString(KEY_KEY, null)
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: return null
        PairedDesktop(serverId, prefs.getString(KEY_NAME, null).orEmpty(), host, port, key,
            prefs.getBoolean(KEY_BOUND, false))
    }

    /** Replaces any earlier pairing and starts from a clean sync state. */
    fun save(d: PairedDesktop) {
        synchronized(this) {
            prefs.edit()
                .putString(KEY_SERVER_ID, d.serverId)
                .putString(KEY_NAME, d.name)
                .putString(KEY_HOST, d.host)
                .putInt(KEY_PORT, d.port)
                .putString(KEY_KEY, Base64.getEncoder().encodeToString(d.key))
                .putBoolean(KEY_BOUND, d.boundConfirmed)
                .remove(KEY_LAST_SYNC_AT)
                .remove(KEY_LAST_ERROR)
                .remove(KEY_SESSION_DIGEST)
                .putBoolean(KEY_SHARE_SESSION, false)
                .apply()
        }
    }

    /**
     * Drops the pairing, the key and everything tied to it, including the
     * consent to receive the YouTube session (it was given for that desktop).
     * The device id stays.
     */
    fun forget() {
        synchronized(this) {
            prefs.edit()
                .remove(KEY_SERVER_ID).remove(KEY_NAME).remove(KEY_HOST).remove(KEY_PORT).remove(KEY_KEY)
                .remove(KEY_LAST_SYNC_AT).remove(KEY_LAST_ERROR).remove(KEY_SHARE_SESSION)
                .remove(KEY_SESSION_DIGEST).remove(KEY_BOUND)
                .apply()
        }
    }

    /** Sticky: once set it is only cleared by [forget] or a new [save]. */
    fun markBoundConfirmed() {
        synchronized(this) {
            if (prefs.contains(KEY_SERVER_ID)) prefs.edit().putBoolean(KEY_BOUND, true).apply()
        }
    }

    /** The desktop moved (new DHCP lease, other port): keep the pairing, change where to reach it. */
    fun updateAddress(host: String, port: Int) {
        synchronized(this) {
            if (!prefs.contains(KEY_SERVER_ID) || host.isBlank() || port !in 1..65535) return
            prefs.edit().putString(KEY_HOST, host).putInt(KEY_PORT, port).apply()
        }
    }

    /** Epoch milliseconds of the last successful sync, 0 when there has been none. */
    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    /** Short code of the last failure (for the UI to translate), null when the last sync worked. */
    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) {
            val edit = prefs.edit()
            if (value == null) edit.remove(KEY_LAST_ERROR) else edit.putString(KEY_LAST_ERROR, value)
            edit.apply()
        }

    /** Consent, given on the phone, to receive the desktop's YouTube session. Off by default. */
    var shareSessionAccepted: Boolean
        get() = prefs.getBoolean(KEY_SHARE_SESSION, false)
        set(value) = prefs.edit().putBoolean(KEY_SHARE_SESSION, value).apply()

    /**
     * Digest of the YouTube session the desktop last handed over, so the same
     * one is not applied again on every sync (each application is a sign-in:
     * it bumps the session generation, and from a device-only profile it would
     * add a profile). Only the digest is kept, never the cookies. Null when no
     * session was received since the pairing or the consent last changed.
     */
    var lastSessionDigest: String?
        get() = prefs.getString(KEY_SESSION_DIGEST, null)
        set(value) {
            val edit = prefs.edit()
            if (value == null) edit.remove(KEY_SESSION_DIGEST) else edit.putString(KEY_SESSION_DIGEST, value)
            edit.apply()
        }

    companion object {
        private const val TAG = "SyncStore"

        /** Named in `backup_rules.xml` and `data_extraction_rules.xml`; rename all three together. */
        const val PREFS_NAME = "triat_sync_pairing"

        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SERVER_ID = "server_id"
        private const val KEY_NAME = "name"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_KEY = "key"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_SHARE_SESSION = "share_session"
        private const val KEY_SESSION_DIGEST = "session_digest"
        private const val KEY_BOUND = "bound_confirmed"

        @Suppress("DEPRECATION")
        private fun buildPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(context, PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

        /**
         * The recovery Last.fm has: an unreadable store (a keystore that
         * refuses, a keyset sealed under a key that is gone, app data moved to
         * a new phone by a tool that ignores the backup rules) is deleted and
         * rebuilt, never allowed to crash the app. Only this file is deleted,
         * never the master key, which the YouTube session and Last.fm stores
         * share. If the rebuild fails too, the pairing lives in memory for
         * this process only: the phone then has to pair again, which is the
         * same outcome as an unreadable store and safe.
         */
        private fun openPrivatePrefs(context: Context): SharedPreferences = try {
            buildPrefs(context)
        } catch (e: Exception) {
            KLog.e(TAG, "Encrypted sync store unreadable, resetting", e)
            try {
                context.deleteSharedPreferences(PREFS_NAME)
                buildPrefs(context)
            } catch (retry: Exception) {
                KLog.e(TAG, "Encrypted sync store unavailable; using volatile store", retry)
                VolatileSharedPreferences()
            }
        }
    }
}
