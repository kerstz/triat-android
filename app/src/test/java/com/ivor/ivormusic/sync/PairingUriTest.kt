package com.ivor.ivormusic.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PairingUriTest {
    @Test fun valid() =
        assertEquals(PairingUri(listOf("192.168.1.5"), 47823, "ABCDEFGH", "ab12"),
            parsePairingUri("triat://pair?host=192.168.1.5&port=47823&code=ABCDEFGH&id=ab12"))

    @Test fun wrongScheme() = assertNull(parsePairingUri("http://pair?host=192.168.1.5&port=1&code=A&id=x"))
    @Test fun missingPort() = assertNull(parsePairingUri("triat://pair?host=192.168.1.5&code=A&id=x"))
    @Test fun portTooBig() = assertNull(parsePairingUri("triat://pair?host=192.168.1.5&port=70000&code=A&id=x"))
    @Test fun publicHostRefused() = assertNull(parsePairingUri("triat://pair?host=8.8.8.8&port=47823&code=A&id=x"))
    @Test fun codeUppercased() =
        assertEquals("ABCDEFGH", parsePairingUri("triat://pair?host=192.168.1.5&port=47823&code=abcd-efgh&id=x")?.code)

    @Test fun severalHostsKeepTheirOrder() {
        val qr = parsePairingUri("triat://pair?host=192.168.1.5&host=10.0.0.7&port=47823&code=ABCDEFGH&id=x")
        assertEquals(listOf("192.168.1.5", "10.0.0.7"), qr?.hosts)
        assertEquals("192.168.1.5", qr?.host)
    }

    @Test fun publicHostsAreDroppedWhenAPrivateOneRemains() {
        val qr = parsePairingUri("triat://pair?host=8.8.8.8&host=100.101.1.2&host=172.17.0.1&port=1&code=A&id=x")
        assertEquals(listOf("172.17.0.1"), qr?.hosts)
    }

    @Test fun duplicateHostsAreCollapsed() {
        val qr = parsePairingUri("triat://pair?host=10.0.0.7&host=10.0.0.7&port=1&code=A&id=x")
        assertEquals(listOf("10.0.0.7"), qr?.hosts)
    }

    @Test fun noPrivateHostFails() =
        assertNull(parsePairingUri("triat://pair?host=8.8.8.8&host=1.1.1.1&port=1&code=A&id=x"))

    // ---- which desktop a code points at ------------------------------------

    private val qr = PairingUri(listOf("10.0.0.7", "192.168.1.5"), 47823, "ABCDEFGH", "srv")
    private fun found(id: String, host: String) = FoundDesktop(id, "pc", host, 47823)

    @Test fun firstAnswerWithTheCodesIdWins() {
        val a = found("srv", "10.0.0.7")
        assertSame(a, matchQrDesktop(qr, listOf(null, a, found("srv", "192.168.1.5")), emptyList()))
    }

    @Test fun anAnswerWithAnotherIdIsNotTheDesktop() {
        assertNull(matchQrDesktop(qr, listOf(found("other", "10.0.0.7")), emptyList()))
    }

    @Test fun mdnsIsTheFallbackAndMustMatchToo() {
        val viaMdns = found("srv", "192.168.1.9")
        assertSame(viaMdns, matchQrDesktop(qr, listOf(null, null), listOf(found("other", "1.1.1.1"), viaMdns)))
        assertNull(matchQrDesktop(qr, listOf(null), listOf(found("other", "192.168.1.9"))))
    }
}
