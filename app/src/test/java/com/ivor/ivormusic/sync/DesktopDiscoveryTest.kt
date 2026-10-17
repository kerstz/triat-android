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
    fun `IPv4 wins over IPv6 and IPv6 is never offered alone`() {
        val v6 = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 1 })
        assertEquals("10.0.0.2", DesktopDiscovery.pickHost(listOf(v6, ip(10, 0, 0, 2))))
        assertNull(DesktopDiscovery.pickHost(listOf(v6)))
    }

    @Test
    fun `a LAN address wins over a virtual interface one`() {
        assertEquals("192.168.1.10", DesktopDiscovery.pickHost(listOf(ip(100, 101, 1, 2), ip(192, 168, 1, 10))))
        assertEquals("10.1.2.3", DesktopDiscovery.pickHost(listOf(ip(169, 254, 3, 4), ip(10, 1, 2, 3))))
        assertEquals("172.20.0.5", DesktopDiscovery.pickHost(listOf(ip(172, 32, 0, 1), ip(172, 20, 0, 5))))
    }

    @Test
    fun `an address the client would refuse is never offered`() {
        assertNull(DesktopDiscovery.pickHost(listOf(ip(100, 101, 1, 2))))
        assertNull(DesktopDiscovery.pickHost(listOf(ip(8, 8, 8, 8))))
        assertEquals("169.254.3.4", DesktopDiscovery.pickHost(listOf(ip(100, 101, 1, 2), ip(169, 254, 3, 4))))
    }

    @Test
    fun `a TXT version other than ours is reported, not dropped`() {
        val newer = txt("id1", "x") + ("v" to "2".toByteArray(Charsets.UTF_8))
        val found = DesktopDiscovery.desktopFrom("svc", newer, listOf(ip(10, 0, 0, 2)), 47823)
        assertEquals(2, found?.version)
        assertEquals(SyncWire.VERSION, DesktopDiscovery.desktopFrom("svc", txt("id1", "x"), listOf(ip(10, 0, 0, 2)), 1)?.version)
    }

    @Test
    fun `loopback is never offered`() {
        assertNull(DesktopDiscovery.pickHost(listOf(ip(127, 0, 0, 1))))
    }
}
