package com.ivor.ivormusic.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingUriTest {
    @Test fun valid() =
        assertEquals(PairingUri("192.168.1.5", 47823, "ABCDEFGH", "ab12"),
            parsePairingUri("triat://pair?host=192.168.1.5&port=47823&code=ABCDEFGH&id=ab12"))

    @Test fun wrongScheme() = assertNull(parsePairingUri("http://pair?host=192.168.1.5&port=1&code=A&id=x"))
    @Test fun missingPort() = assertNull(parsePairingUri("triat://pair?host=192.168.1.5&code=A&id=x"))
    @Test fun portTooBig() = assertNull(parsePairingUri("triat://pair?host=192.168.1.5&port=70000&code=A&id=x"))
    @Test fun publicHostRefused() = assertNull(parsePairingUri("triat://pair?host=8.8.8.8&port=47823&code=A&id=x"))
    @Test fun codeUppercased() =
        assertEquals("ABCDEFGH", parsePairingUri("triat://pair?host=192.168.1.5&port=47823&code=abcd-efgh&id=x")?.code)
}
