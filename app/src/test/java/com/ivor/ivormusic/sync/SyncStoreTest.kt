package com.ivor.ivormusic.sync

import com.ivor.ivormusic.data.VolatileSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStoreTest {

    private val desktop = SyncStore.PairedDesktop(
        serverId = "abc123", name = "Triat - test-pc", host = "192.168.1.20", port = 47823,
        key = ByteArray(32) { it.toByte() },
    )

    @Test
    fun `nothing is paired at first and the share consent is off`() {
        val store = SyncStore(VolatileSharedPreferences())
        assertNull(store.paired())
        assertFalse(store.shareSessionAccepted)
        assertEquals(0L, store.lastSyncAt)
        assertNull(store.lastError)
    }

    @Test
    fun `the device id is generated once and kept`() {
        val prefs = VolatileSharedPreferences()
        val first = SyncStore(prefs).deviceId
        assertTrue(first.isNotBlank())
        assertEquals(first, SyncStore(prefs).deviceId)
        assertNotEquals(first, SyncStore(VolatileSharedPreferences()).deviceId)
    }

    @Test
    fun `a saved pairing comes back intact, key included`() {
        val prefs = VolatileSharedPreferences()
        SyncStore(prefs).save(desktop)
        val back = SyncStore(prefs).paired()
        assertEquals(desktop, back)
        assertEquals(desktop.hashCode(), back.hashCode())
    }

    @Test
    fun `equality looks at the key bytes`() {
        assertNotEquals(desktop, desktop.copy(key = ByteArray(32)))
        assertEquals(desktop, desktop.copy(key = desktop.key.copyOf()))
    }

    @Test
    fun `the key never shows in the text form`() {
        assertFalse(desktop.toString().contains("key"))
    }

    @Test
    fun `updating the address keeps the rest`() {
        val store = SyncStore(VolatileSharedPreferences())
        store.save(desktop)
        store.updateAddress("192.168.1.99", 50000)
        assertEquals(desktop.copy(host = "192.168.1.99", port = 50000), store.paired())
    }

    @Test
    fun `updating the address of nobody does nothing`() {
        val store = SyncStore(VolatileSharedPreferences())
        store.updateAddress("192.168.1.99", 50000)
        assertNull(store.paired())
    }

    @Test
    fun `forget drops the pairing and its state but keeps the device id`() {
        val store = SyncStore(VolatileSharedPreferences())
        val id = store.deviceId
        store.save(desktop)
        store.lastSyncAt = 1_700_000_000_000
        store.lastError = "unpaired"
        store.shareSessionAccepted = true
        store.forget()
        assertNull(store.paired())
        assertEquals(0L, store.lastSyncAt)
        assertNull(store.lastError)
        assertFalse(store.shareSessionAccepted)
        assertEquals(id, store.deviceId)
    }

    @Test
    fun `pairing again starts from a clean sync state`() {
        val store = SyncStore(VolatileSharedPreferences())
        store.save(desktop)
        store.lastSyncAt = 5L
        store.lastError = "boom"
        store.shareSessionAccepted = true
        store.save(desktop.copy(serverId = "other"))
        assertEquals(0L, store.lastSyncAt)
        assertNull(store.lastError)
        assertFalse(store.shareSessionAccepted)
    }

    @Test
    fun `a damaged record reads as not paired`() {
        val prefs = VolatileSharedPreferences()
        SyncStore(prefs).save(desktop)
        prefs.edit().putString("key", "not base64 !!").commit()
        assertNull(SyncStore(prefs).paired())
    }
}
