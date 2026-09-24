package com.ivor.ivormusic.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

/** The pure part of the desktop discovery: TXT decoding and address choice. */
class DesktopDiscoveryTest {

    private fun ip(vararg octets: Int): InetAddress =
        InetAddress.getByAddress(ByteArray(octets.size) { octets[it].toByte() })

    private fun txt(id: String?, name: String?): Map<String, ByteArray?> = buildMap {
        if (id != null) put("id", id.toByteArray(Charsets.UTF_8))
        if (name != null) put("name", name.toByteArray(Charsets.UTF_8))
        put("v", "1".toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `a service with an id and a name becomes a desktop`() {
        val found = DesktopDiscovery.desktopFrom(
            "svc", txt("abcdef0123456789", "Triat — test-pc"), listOf(ip(192, 168, 1, 20)), 47823,
        )
        assertEquals(FoundDesktop("abcdef0123456789", "Triat — test-pc", "192.168.1.20", 47823), found)
    }

    @Test
    fun `a service without an id is not a Triat desktop`() {
        assertNull(DesktopDiscovery.desktopFrom("svc", txt(null, "x"), listOf(ip(192, 168, 1, 20)), 47823))
        assertNull(DesktopDiscovery.desktopFrom("svc", txt("  ", "x"), listOf(ip(192, 168, 1, 20)), 47823))
    }

    @Test
    fun `the name falls back to the service name`() {
        val found = DesktopDiscovery.desktopFrom("svc-name", txt("id1", null), listOf(ip(10, 0, 0, 2)), 5000)
        assertEquals("svc-name", found?.name)
    }

    @Test
    fun `no address or a bad port drops the service`() {
        assertNull(DesktopDiscovery.desktopFrom("svc", txt("id1", "x"), emptyList(), 47823))
        assertNull(DesktopDiscovery.desktopFrom("svc", txt("id1", "x"), listOf(ip(10, 0, 0, 2)), 0))
    }

    @Test
    fun `IPv4 wins over IPv6`() {
        val v6 = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 1 })
        assertEquals("10.0.0.2", DesktopDiscovery.pickHost(listOf(v6, ip(10, 0, 0, 2))))
    }

    @Test
    fun `IPv6 is bracketed and link-local is skipped`() {
        val ula = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 1 })
        val linkLocal = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0xfe.toByte(); it[1] = 0x80.toByte(); it[15] = 1 })
        assertEquals("[fd00:0:0:0:0:0:0:1]", DesktopDiscovery.pickHost(listOf(linkLocal, ula)))
        assertNull(DesktopDiscovery.pickHost(listOf(linkLocal)))
    }

    @Test
    fun `loopback is never offered`() {
        assertNull(DesktopDiscovery.pickHost(listOf(ip(127, 0, 0, 1))))
    }
}
