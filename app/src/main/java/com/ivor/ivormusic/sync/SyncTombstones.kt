package com.ivor.ivormusic.sync

import android.content.Context
import org.json.JSONObject

/**
 * Deletions the Wi-Fi sync must carry to the desktop: a removed playlist or
 * an unliked song. Without them the other device would hand the item back
 * on the next sync. Kept in their own small preference file.
 */
class SyncTombstones(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("triat_sync_tombstones", Context.MODE_PRIVATE)

    fun record(kind: String, key: String, at: Long = System.currentTimeMillis()) {
        val map = read(kind)
        if ((map.optLong(key, 0L)) < at) {
            map.put(key, at)
            prefs.edit().putString(kind, map.toString()).apply()
        }
    }

    fun clear(kind: String, key: String) {
        val map = read(kind)
        if (map.has(key)) {
            map.remove(key)
            prefs.edit().putString(kind, map.toString()).apply()
        }
    }

    fun all(kind: String): Map<String, Long> {
        val map = read(kind)
        return map.keys().asSequence().associateWith { map.optLong(it) }
    }

    private fun read(kind: String): JSONObject =
        runCatching { JSONObject(prefs.getString(kind, "{}") ?: "{}") }.getOrElse { JSONObject() }

    companion object {
        const val PLAYLIST = "playlist"
        const val LIKE = "like"
    }
}
