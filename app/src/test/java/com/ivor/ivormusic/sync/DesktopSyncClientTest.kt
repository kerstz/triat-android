package com.ivor.ivormusic.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The HTTP client of the desktop sync. The exchange is tested against a
 * desktop simulated in this file, which speaks the protocol of section 1 of
 * docs/sync.md with the real [SyncCrypto] (the JDK's own HTTP server,
 * on the loopback address, so no dependency is added). The guard that keeps
 * the sync on the local network is tested both as a pure function and through
 * the client.
 */
class DesktopSyncClientTest {

    private val servers = mutableListOf<HttpServer>()

    @After
    fun stopServers() {
        servers.forEach { it.stop(0) }
    }

    // ---- isPrivateHost --------------------------------------------------

    @Test
    fun `private and link-local addresses are accepted`() {
        listOf(
            "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.20.5.5", "172.31.255.255",
            "192.168.0.1", "192.168.1.20", "169.254.0.1", "169.254.10.10",
            "fe80::1", "fe80::abcd:1234", "febf::1", "fc00::1", "fd12:3456:789a::1",
            "[fe80::1]", "[fd12::5]", "::ffff:192.168.1.5",
            "pc.local", "PC.LOCAL", "my-desktop.local", "living_room.local", "a.b.local", "192-168-1-5.local",
        ).forEach { assertTrue("$it should be accepted", isPrivateHost(it)) }
    }

    @Test
    fun `loopback addresses are refused in production`() {
        listOf("127.0.0.1", "127.255.0.3", "127.0.0.0", "::1", "[::1]", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1")
            .forEach { assertFalse("$it should be refused", isPrivateHost(it)) }
        // Only the test switch lets them through.
        listOf("127.0.0.1", "127.255.0.3", "::1", "[::1]")
            .forEach { assertTrue("$it should pass the test switch", isPrivateHost(it, allowLoopback = true)) }
        assertFalse(isPrivateHost("8.8.8.8", allowLoopback = true))
    }

    @Test
    fun `a client with the defaults refuses a desktop on the phone itself`() = runBlocking {
        val desktop = FakeDesktop().start()
        val client = DesktopSyncClient()
        val key = ByteArray(32) { 1 }
        for (host in listOf("127.0.0.1", "::1", "[::1]")) {
            assertNull(client.hello(host, desktop.port))
            assertEquals(
                PairResult.Failed(DesktopSyncClient.REASON_HOST),
                client.pair(host, desktop.port, "K7QM2XPA", "phone-1", "Phone"),
            )
            assertEquals(
                SyncResult.Failed(DesktopSyncClient.REASON_HOST),
                client.sync(SyncStore.PairedDesktop("id", "n", host, desktop.port, key), "phone-1", JSONObject()),
            )
            assertFalse(client.unpair(SyncStore.PairedDesktop("id", "n", host, desktop.port, key), "phone-1"))
        }
        assertThrows(IllegalArgumentException::class.java) { DesktopSyncClient.urlFor("127.0.0.1", 47823, "v1/hello") }
        Unit
    }

    @Test
    fun `public addresses and names are refused`() {
        listOf(
            "8.8.8.8", "1.1.1.1", "93.184.216.34", "0.0.0.0", "255.255.255.255",
            "172.15.255.255", "172.32.0.1", "192.167.1.1", "192.169.1.1", "169.253.1.1", "11.0.0.1",
            "100.64.0.1", "198.51.100.7",
            "2001:db8::1", "2606:4700:4700::1111", "::", "fec0::1", "fe00::1", "fbff::1", "fe7f::1", "ff02::1",
            "::ffff:8.8.8.8", "[2001:db8::1]",
            "example.com", "www.example.com", "localhost", "github.com", "local", ".local", "pc.local.",
            "evil.com.local.evil.com", "a.local.com", "local.example.com",
        ).forEach { assertFalse("$it should be refused", isPrivateHost(it)) }
    }

    @Test
    fun `ambiguous or malformed hosts are refused`() {
        listOf(
            "", " ", " 192.168.1.1", "192.168.1.1 ", "192.168.1.1:47823", "http://192.168.1.1",
            "192.168.1.1/", "192.168.1.1/x", "user@192.168.1.1", "192.168.1.1#.evil.com",
            "10.0.0.1.evil.com", "10.0.0.256", "10.0.0", "10.0.0.1.1", "127.1", "2130706433", "0x7f.0.0.1",
            "010.0.0.1", "192.168.001.001", "10.0.0.-1", "10.0.0.+1",
            "[192.168.1.1]", "[fe80::1", "fe80::1]", "fe80::1%wlan0", "[fe80::1%25wlan0]", "fe80::gggg",
            "fe80:::1", "1:2:3:4:5:6:7:8:9",
            "a..local", "-a.local", "a-.local", "a b.local", "a/b.local", "a@b.local", "a#.local", "a?.local",
            "٣.٣.٣.٣", "café.local", "pcK.local",
        ).forEach { assertFalse("'$it' should be refused", isPrivateHost(it)) }
        assertFalse(isPrivateHost("a".repeat(70) + ".local"))
        assertFalse(isPrivateHost("a.".repeat(130) + "local"))
    }

    @Test
    fun `a name is only usable when it resolves to private addresses only`() {
        fun dnsOf(vararg octets: IntArray) = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                octets.map { o -> InetAddress.getByAddress(ByteArray(4) { o[it].toByte() }) }
        }
        val home = intArrayOf(192, 168, 1, 5)
        val lan10 = intArrayOf(10, 0, 0, 7)
        val public = intArrayOf(93, 184, 216, 34)

        assertEquals(2, PrivateOnlyDns(dnsOf(home, lan10)).lookup("pc.local").size)
        assertThrows(UnknownHostException::class.java) { PrivateOnlyDns(dnsOf(public)).lookup("pc.local") }
        assertThrows(UnknownHostException::class.java) { PrivateOnlyDns(dnsOf(home, public)).lookup("pc.local") }
        assertThrows(UnknownHostException::class.java) { PrivateOnlyDns(dnsOf()).lookup("pc.local") }
        // A .local name pointing at the phone itself is refused too.
        assertThrows(UnknownHostException::class.java) { PrivateOnlyDns(dnsOf(intArrayOf(127, 0, 0, 1))).lookup("pc.local") }
        // A public name never reaches the resolver at all.
        val untouched = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = error("must not be called")
        }
        assertThrows(UnknownHostException::class.java) { PrivateOnlyDns(untouched).lookup("example.com") }
    }

    @Test
    fun `the url is built only for a private host`() {
        assertEquals("http://192.168.1.5:47823/v1/hello", DesktopSyncClient.urlFor("192.168.1.5", 47823, "v1/hello").toString())
        assertEquals("http://[fe80::1]:47823/v1/sync", DesktopSyncClient.urlFor("[fe80::1]", 47823, "v1/sync").toString())
        assertEquals("http://pc.local:5000/v1/hello", DesktopSyncClient.urlFor("pc.local", 5000, "v1/hello").toString())
        assertThrows(IllegalArgumentException::class.java) { DesktopSyncClient.urlFor("8.8.8.8", 47823, "v1/hello") }
        assertThrows(IllegalArgumentException::class.java) { DesktopSyncClient.urlFor("example.com", 47823, "v1/hello") }
        assertThrows(IllegalArgumentException::class.java) { DesktopSyncClient.urlFor("192.168.1.5", 0, "v1/hello") }
    }

    // ---- the guard through the client -----------------------------------

    @Test
    fun `every entry point refuses a public host before sending anything`() = runBlocking {
        val client = testClient()
        val key = ByteArray(32) { 1 }
        for (host in listOf("8.8.8.8", "example.com", "2001:db8::1", "localhost")) {
            assertNull(client.hello(host, 47823))
            assertEquals(
                PairResult.Failed(DesktopSyncClient.REASON_HOST),
                client.pair(host, 47823, "K7QM2XPA", "phone-1", "Phone"),
            )
            assertEquals(
                SyncResult.Failed(DesktopSyncClient.REASON_HOST),
                client.sync(SyncStore.PairedDesktop("id", "n", host, 47823, key), "phone-1", JSONObject()),
            )
        }
    }

    @Test
    fun `a redirect to another address is not followed`() = runBlocking {
        val elsewhere = FakeDesktop().start()
        val redirecting = FakeDesktop().start()
        redirecting.redirectTo = "http://127.0.0.1:${elsewhere.port}/v1/hello"

        assertNull(testClient().hello("127.0.0.1", redirecting.port))
        assertEquals(listOf("GET /v1/hello"), redirecting.requests)
        assertTrue("the redirect target was contacted", elsewhere.requests.isEmpty())
    }

    // ---- hello ----------------------------------------------------------

    @Test
    fun `hello identifies a desktop`() = runBlocking {
        val desktop = FakeDesktop().start()
        val found = testClient().hello("127.0.0.1", desktop.port)
        assertEquals(FoundDesktop("0123abcd0123abcd", "Triat — test-pc", "127.0.0.1", desktop.port), found)
    }

    @Test
    fun `hello refuses what is not a Triat desktop`() = runBlocking {
        val client = testClient()
        val other = FakeDesktop().start().also { it.helloJson = """{"app":"something","v":1,"server_id":"x","name":"y"}""" }
        assertNull(client.hello("127.0.0.1", other.port))
        val noId = FakeDesktop().start().also { it.helloJson = """{"app":"triat","v":1,"server_id":null,"name":"y"}""" }
        assertNull(client.hello("127.0.0.1", noId.port))
        val garbage = FakeDesktop().start().also { it.helloJson = "<html>hello</html>" }
        assertNull(client.hello("127.0.0.1", garbage.port))
        assertNull(client.hello("127.0.0.1", closedPort()))
    }

    @Test
    fun `hello reports another protocol version instead of not found`() = runBlocking {
        val newer = FakeDesktop().start().also { it.helloJson = """{"app":"triat","v":2,"server_id":"x","name":"y"}""" }
        val found = testClient().hello("127.0.0.1", newer.port)
        assertEquals(2, found?.version)
        assertEquals("x", found?.serverId)
    }

    @Test
    fun `hello carries the desktop clock when it sends one`() {
        val body = """{"app":"triat","v":1,"server_id":"x","name":"y","now":1700000000000}""".toByteArray()
        assertEquals(1_700_000_000_000L, DesktopSyncClient.parseHello(body, "10.0.0.2", 47823)?.serverNow)
        val without = """{"app":"triat","v":1,"server_id":"x","name":"y"}""".toByteArray()
        assertNull(DesktopSyncClient.parseHello(without, "10.0.0.2", 47823)?.serverNow)
    }

    @Test
    fun `a cancelled caller gets control back at once`() {
        val slow = FakeDesktop().start().also { it.delayMs = 1500 }
        val started = System.nanoTime()
        try {
            runBlocking { withTimeout(200) { testClient().hello("127.0.0.1", slow.port) } }
            error("hello should not have answered")
        } catch (expected: TimeoutCancellationException) {
            // The point: control came back well before the slow answer would have.
            assertTrue((System.nanoTime() - started) / 1_000_000 < 1000)
        }
    }

    // ---- pairing --------------------------------------------------------

    @Test
    fun `pairing with the right code stores the shared key`() = runBlocking {
        val desktop = FakeDesktop().start()
        val result = testClient().pair("127.0.0.1", desktop.port, "k7qm-2xpa", "phone-1", "Test phone")

        assertTrue(result.toString(), result is PairResult.Paired)
        val paired = (result as PairResult.Paired).desktop
        assertEquals("0123abcd0123abcd", paired.serverId)
        assertEquals("Triat — test-pc", paired.name)
        assertEquals("127.0.0.1", paired.host)
        assertEquals(desktop.port, paired.port)
        assertEquals(32, paired.key.size)
        assertArrayEquals(desktop.pairedKey, paired.key)
        assertEquals("phone-1", desktop.pairedDeviceId)
        assertEquals("Test phone", desktop.pairedName)
        assertEquals(listOf("POST /v1/pair/start", "POST /v1/pair/finish"), desktop.requests)
    }

    @Test
    fun `a wrong code is reported as such`() = runBlocking {
        val desktop = FakeDesktop().start()
        assertEquals(PairResult.WrongCode, testClient().pair("127.0.0.1", desktop.port, "AAAAAAAA", "phone-1", "Phone"))
        assertNull(desktop.pairedKey)
    }

    @Test
    fun `a code of the wrong length does not cost an attempt`() = runBlocking {
        val desktop = FakeDesktop().start()
        val client = testClient()
        assertEquals(PairResult.WrongCode, client.pair("127.0.0.1", desktop.port, "K7QM", "phone-1", "Phone"))
        assertEquals(PairResult.WrongCode, client.pair("127.0.0.1", desktop.port, "", "phone-1", "Phone"))
        assertTrue(desktop.requests.isEmpty())
    }

    @Test
    fun `pairing is closed when the desktop shows no code`() = runBlocking {
        val closedAtStart = FakeDesktop().start().also { it.pairingOpen = false }
        assertEquals(PairResult.PairingClosed, testClient().pair("127.0.0.1", closedAtStart.port, "K7QM2XPA", "phone-1", "Phone"))

        val closedAtFinish = FakeDesktop().start().also { it.finishStatus = 409 }
        assertEquals(PairResult.PairingClosed, testClient().pair("127.0.0.1", closedAtFinish.port, "K7QM2XPA", "phone-1", "Phone"))
    }

    @Test
    fun `a desktop that cannot prove it knows the code is not recorded`() = runBlocking {
        val impostor = FakeDesktop().start().also { it.serverProofOverride = ByteArray(32) { 7 } }
        assertEquals(
            PairResult.Failed(DesktopSyncClient.REASON_PROOF),
            testClient().pair("127.0.0.1", impostor.port, "K7QM2XPA", "phone-1", "Phone"),
        )
    }

    @Test
    fun `answers that are not the protocol fail cleanly`() = runBlocking {
        val client = testClient()
        val badKey = FakeDesktop().start().also { it.startJson = """{"server_id":"a","name":"b","pub":"AAAA"}""" }
        assertEquals(PairResult.Failed(DesktopSyncClient.REASON_PROTOCOL), client.pair("127.0.0.1", badKey.port, "K7QM2XPA", "phone-1", "P"))

        // 65 bytes with the right prefix, but not a point of the curve.
        val offCurve = SyncCrypto.b64(byteArrayOf(0x04) + ByteArray(64) { 5 })
        val notAPoint = FakeDesktop().start().also { it.startJson = """{"server_id":"a","name":"b","pub":"$offCurve"}""" }
        assertEquals(PairResult.Failed(DesktopSyncClient.REASON_PROTOCOL), client.pair("127.0.0.1", notAPoint.port, "K7QM2XPA", "phone-1", "P"))

        val html = FakeDesktop().start().also { it.startJson = "<html></html>" }
        assertEquals(PairResult.Failed(DesktopSyncClient.REASON_PROTOCOL), client.pair("127.0.0.1", html.port, "K7QM2XPA", "phone-1", "P"))

        val broken = FakeDesktop().start().also { it.startStatus = 500 }
        assertEquals(PairResult.Failed(DesktopSyncClient.REASON_SERVER), client.pair("127.0.0.1", broken.port, "K7QM2XPA", "phone-1", "P"))

        assertEquals(PairResult.Failed(DesktopSyncClient.REASON_NETWORK), client.pair("127.0.0.1", closedPort(), "K7QM2XPA", "phone-1", "P"))
    }

    @Test
    fun `a device id that cannot travel is refused`() = runBlocking {
        val client = testClient()
        for (id in listOf("", "a".repeat(65), "with space", "café", "line\nbreak")) {
            assertEquals(PairResult.Failed(DesktopSyncClient.REASON_DEVICE), client.pair("127.0.0.1", 47823, "K7QM2XPA", id, "P"))
        }
        assertTrue(DesktopSyncClient.isValidDeviceId("6f1d9c8e-1b2a-4c3d-8e9f-0a1b2c3d4e5f"))
        assertTrue(DesktopSyncClient.isValidDeviceId("a".repeat(64)))
    }

    // ---- sync -----------------------------------------------------------

    private suspend fun pairedWith(desktop: FakeDesktop): SyncStore.PairedDesktop {
        val result = testClient().pair("127.0.0.1", desktop.port, "K7QM2XPA", "phone-1", "Phone")
        return (result as PairResult.Paired).desktop
    }

    @Test
    fun `a sync sends the sealed snapshot and returns the merged state`() = runBlocking {
        val desktop = FakeDesktop().start()
        val paired = pairedWith(desktop)
        desktop.mergedJson = """{"v":1,"playlists":[{"uid":"u1","name":"Mix","updated":1700000000000,"deleted":false,"tracks":[]}],"likes":[],"history":[],"cookies":"a=b; c=d"}"""

        val snapshot = SyncWire.snapshot(emptyList(), emptyList(), emptyMap(), emptyList()).put("marker", "from-the-phone")
        val result = testClient().sync(paired, "phone-1", snapshot)

        assertTrue(result.toString(), result is SyncResult.Ok)
        val merged = (result as SyncResult.Ok).merged
        assertEquals("Mix", merged.getJSONArray("playlists").getJSONObject(0).getString("name"))
        assertEquals("a=b; c=d", merged.getString("cookies"))
        assertEquals("phone-1", desktop.lastSyncDevice)
        assertEquals("from-the-phone", desktop.lastSyncReceived?.getString("marker"))
        assertEquals(1, desktop.lastSyncReceived?.getInt("v"))
    }

    @Test
    fun `a sync uses the bound framing with fresh ts and n`() = runBlocking {
        val desktop = FakeDesktop().start()
        val paired = pairedWith(desktop)
        val client = testClient()
        val first = client.sync(paired, "phone-1", JSONObject().put("v", 1)) as SyncResult.Ok
        val second = client.sync(paired, "phone-1", JSONObject().put("v", 1)) as SyncResult.Ok
        assertTrue(first.bound && second.bound)
        assertEquals(listOf("bound", "bound"), desktop.syncRequests.toList())
        assertEquals(2, desktop.seenNonces.toSet().size)
        assertTrue(kotlin.math.abs(System.currentTimeMillis() - desktop.lastSyncReceived!!.getLong("ts")) < 60_000)
    }

    @Test
    fun `an older desktop is reached through the legacy framing until the bound one is confirmed`() = runBlocking {
        val desktop = FakeDesktop().start()
        val paired = pairedWith(desktop)
        desktop.legacyOnly = true
        val result = testClient().sync(paired, "phone-1", JSONObject().put("v", 1))
        assertTrue(result is SyncResult.Ok)
        assertFalse((result as SyncResult.Ok).bound)
        assertEquals(listOf("legacy"), desktop.syncRequests.toList())
    }

    @Test
    fun `once the bound framing is confirmed there is no fallback to the legacy one`() = runBlocking {
        val desktop = FakeDesktop().start()
        val paired = pairedWith(desktop).copy(boundConfirmed = true)
        desktop.legacyOnly = true
        assertEquals(SyncResult.Unauthorized, testClient().sync(paired, "phone-1", JSONObject().put("v", 1)))
        assertTrue(desktop.syncRequests.isEmpty())
    }

    @Test
    fun `a reply sealed for another request is refused`() = runBlocking {
        val desktop = FakeDesktop().start()
        val paired = pairedWith(desktop)
        desktop.wrongReplyNonce = "00000000000000000000000000000000"
        assertEquals(
            SyncResult.Failed(DesktopSyncClient.REASON_PROTOCOL),
            testClient().sync(paired, "phone-1", JSONObject().put("v", 1)),
        )
    }

    @Test
    fun `unpair tells the desktop and never throws`() = runBlocking {
        val desktop = FakeDesktop().start()
        val paired = pairedWith(desktop)
        assertTrue(testClient().unpair(paired, "phone-1"))
        assertTrue(desktop.unpaired)
        assertFalse(testClient().unpair(paired.copy(port = closedPort()), "phone-1"))
    }

    @Test
    fun `an unknown phone is reported as unauthorized`() = runBlocking {
        val desktop = FakeDesktop().start()
        val paired = pairedWith(desktop)
        desktop.syncStatus = 401
        assertEquals(SyncResult.Unauthorized, testClient().sync(paired, "phone-1", JSONObject().put("v", 1)))

        // A key that is not the desktop's gets the same answer from the real desktop.
        val other = FakeDesktop().start().also { pairedWith(it) }
        val wrongKey = paired.copy(port = other.port, key = ByteArray(32) { 9 })
        assertEquals(SyncResult.Unauthorized, testClient().sync(wrongKey, "phone-1", JSONObject().put("v", 1)))
    }

    @Test
    fun `other statuses and bad answers are failures, never crashes`() = runBlocking {
        val client = testClient()
        val desktop = FakeDesktop().start()
        val paired = pairedWith(desktop)
        val snapshot = JSONObject().put("v", 1)

        desktop.syncStatus = 400
        assertEquals(SyncResult.Failed(DesktopSyncClient.REASON_VERSION), client.sync(paired, "phone-1", snapshot))

        desktop.syncStatus = 503
        assertEquals(SyncResult.Failed(DesktopSyncClient.REASON_SERVER), client.sync(paired, "phone-1", snapshot))

        desktop.syncStatus = 200
        desktop.tamperReply = true
        assertEquals(SyncResult.Failed(DesktopSyncClient.REASON_PROTOCOL), client.sync(paired, "phone-1", snapshot))

        desktop.tamperReply = false
        desktop.rawReply = "not sealed".toByteArray()
        assertEquals(SyncResult.Failed(DesktopSyncClient.REASON_PROTOCOL), client.sync(paired, "phone-1", snapshot))

        desktop.rawReply = null
        desktop.mergedJson = """{"v":2}"""
        assertEquals(SyncResult.Failed(DesktopSyncClient.REASON_VERSION), client.sync(paired, "phone-1", snapshot))

        desktop.mergedJson = "[1,2]"
        assertEquals(SyncResult.Failed(DesktopSyncClient.REASON_PROTOCOL), client.sync(paired, "phone-1", snapshot))

        assertEquals(
            SyncResult.Failed(DesktopSyncClient.REASON_NETWORK),
            client.sync(paired.copy(port = closedPort()), "phone-1", snapshot),
        )
    }

    // ---- the simulated desktop ------------------------------------------

    /** The client under test: the production class, with the loopback switch the simulated desktop needs. */
    private fun testClient() = DesktopSyncClient(OkHttpClient(), allowLoopbackForTests = true)

    private fun closedPort(): Int = ServerSocket(0, 1, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))).use { it.localPort }

    private inner class FakeDesktop(private val code: String = "K7QM2XPA") {
        private val server: HttpServer = HttpServer.create(
            InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0), 0,
        )
        val port: Int get() = server.address.port
        val requests = CopyOnWriteArrayList<String>()

        // Knobs of the tests.
        @Volatile var helloJson = """{"app":"triat","v":1,"server_id":"0123abcd0123abcd","name":"Triat — test-pc","pairing":true}"""
        @Volatile var pairingOpen = true
        @Volatile var redirectTo: String? = null
        @Volatile var delayMs = 0L
        @Volatile var startStatus = 200
        @Volatile var startJson: String? = null
        @Volatile var finishStatus = 200
        @Volatile var serverProofOverride: ByteArray? = null
        @Volatile var syncStatus = 200
        @Volatile var mergedJson = """{"v":1,"playlists":[],"likes":[],"history":[]}"""
        @Volatile var tamperReply = false
        @Volatile var rawReply: ByteArray? = null
        /** Behaves like a desktop published before the bound framing: only the legacy AAD opens. */
        @Volatile var legacyOnly = false
        /** Seals the reply for another request's nonce. */
        @Volatile var wrongReplyNonce: String? = null
        val seenNonces = CopyOnWriteArrayList<String>()
        val syncRequests = CopyOnWriteArrayList<String>()

        // What the desktop learned.
        @Volatile var pairedKey: ByteArray? = null
        @Volatile var pairedDeviceId: String? = null
        @Volatile var pairedName: String? = null
        @Volatile var lastSyncDevice: String? = null
        @Volatile var lastSyncReceived: JSONObject? = null

        private var pendingKey: ByteArray? = null
        private var pendingTranscript: ByteArray? = null
        private var pendingDevice: String? = null
        private var pendingName: String? = null
        private val serverPub = SyncCrypto.newKeyPair()

        fun start(): FakeDesktop {
            server.createContext("/") { ex -> handle(ex) }
            server.start()
            servers += server
            return this
        }

        private fun handle(ex: HttpExchange) {
            ex.use {
                val label = "${it.requestMethod} ${it.requestURI.path}"
                requests += label
                val body = it.requestBody.readBytes()
                if (delayMs > 0) Thread.sleep(delayMs)
                redirectTo?.let { target ->
                    it.responseHeaders.add("Location", target)
                    it.sendResponseHeaders(302, -1)
                    return
                }
                when (label) {
                    "GET /v1/hello" -> json(it, 200, helloJson)
                    "POST /v1/pair/start" -> start(it, body)
                    "POST /v1/pair/finish" -> finish(it, body)
                    "POST /v1/sync" -> sync(it, body)
                    "POST /v1/unpair" -> unpair(it, body)
                    else -> json(it, 404, """{"error":"not-found"}""")
                }
            }
        }

        private fun start(ex: HttpExchange, body: ByteArray) {
            if (startStatus != 200) return json(ex, startStatus, """{"error":"x"}""")
            if (!pairingOpen) return json(ex, 409, """{"error":"pairing-closed"}""")
            startJson?.let { return json(ex, 200, it) }
            val o = JSONObject(String(body, Charsets.UTF_8))
            val clientPub = SyncCrypto.unb64(o.getString("pub"))
            val ownPub = SyncCrypto.encodePublic(serverPub.public)
            pendingDevice = o.getString("device_id")
            pendingName = o.getString("name")
            pendingKey = SyncCrypto.deriveKey(serverPub.private, clientPub, code)
            pendingTranscript = SyncCrypto.transcript(pendingDevice!!, clientPub, ownPub)
            json(
                ex, 200,
                JSONObject().put("server_id", "0123abcd0123abcd").put("name", "Triat — test-pc")
                    .put("pub", SyncCrypto.b64(ownPub)).toString(),
            )
        }

        private fun finish(ex: HttpExchange, body: ByteArray) {
            if (finishStatus != 200) return json(ex, finishStatus, """{"error":"x"}""")
            val key = pendingKey ?: return json(ex, 409, """{"error":"pairing-closed"}""")
            val transcript = pendingTranscript!!
            val proof = SyncCrypto.unb64(JSONObject(String(body, Charsets.UTF_8)).getString("confirm"))
            if (!proof.contentEquals(SyncCrypto.confirmation(key, "client", transcript))) {
                pendingKey = null
                return json(ex, 403, """{"error":"wrong-code"}""")
            }
            pairedKey = key
            pairedDeviceId = pendingDevice
            pairedName = pendingName
            val answer = serverProofOverride ?: SyncCrypto.confirmation(key, "server", transcript)
            json(ex, 200, JSONObject().put("confirm", SyncCrypto.b64(answer)).toString())
        }

        private fun sync(ex: HttpExchange, body: ByteArray) {
            val device = ex.requestHeaders.getFirst("X-Triat-Device").orEmpty()
            if (syncStatus != 200) return raw(ex, syncStatus, ByteArray(0))
            val key = pairedKey ?: return raw(ex, 401, ByteArray(0))
            var requestNonce: String? = null
            var received: JSONObject? = null
            if (!legacyOnly) {
                try {
                    val o = JSONObject(SyncCrypto.openWithAad(key, body, SyncCrypto.requestAad(device)))
                    requestNonce = o.getString("n")
                    seenNonces += requestNonce
                    received = o
                } catch (e: Exception) {
                }
            }
            if (received == null) {
                received = try {
                    JSONObject(SyncCrypto.open(key, body, device))
                } catch (e: Exception) {
                    return raw(ex, 401, ByteArray(0))
                }
            }
            lastSyncDevice = device
            lastSyncReceived = received
            syncRequests += if (requestNonce != null) "bound" else "legacy"
            rawReply?.let { return raw(ex, 200, it) }
            val sealed = if (requestNonce == null) {
                SyncCrypto.seal(key, mergedJson, device)
            } else {
                SyncCrypto.sealWithAad(key, mergedJson, SyncCrypto.responseAad(device, wrongReplyNonce ?: requestNonce))
            }
            if (tamperReply) sealed[sealed.size - 1] = (sealed.last().toInt() xor 1).toByte()
            raw(ex, 200, sealed)
        }

        @Volatile var unpaired = false

        private fun unpair(ex: HttpExchange, body: ByteArray) {
            val device = ex.requestHeaders.getFirst("X-Triat-Device").orEmpty()
            val key = pairedKey ?: return raw(ex, 401, ByteArray(0))
            val n = try {
                JSONObject(SyncCrypto.openWithAad(key, body, SyncCrypto.requestAad(device))).getString("n")
            } catch (e: Exception) {
                return raw(ex, 401, ByteArray(0))
            }
            unpaired = true
            raw(ex, 200, SyncCrypto.sealWithAad(key, """{"ok":true}""", SyncCrypto.responseAad(device, n)))
        }

        private fun json(ex: HttpExchange, status: Int, text: String) {
            ex.responseHeaders.add("Content-Type", "application/json")
            raw(ex, status, text.toByteArray(Charsets.UTF_8))
        }

        private fun raw(ex: HttpExchange, status: Int, bytes: ByteArray) {
            if (bytes.isEmpty()) {
                ex.sendResponseHeaders(status, -1)
            } else {
                ex.sendResponseHeaders(status, bytes.size.toLong())
                ex.responseBody.write(bytes)
            }
        }
    }
}
