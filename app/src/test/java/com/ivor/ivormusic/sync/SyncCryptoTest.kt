package com.ivor.ivormusic.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

/**
 * Interop with Triat desktop. The vectors were produced by the desktop's
 * `triat/core/sync.py` with fixed keys: if one of these fails, the phone and
 * the desktop no longer understand each other.
 */
class SyncCryptoTest {

    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val clientScalar = BigInteger("1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF", 16)
    private val clientPub = hex("04471c3e758c4904285bba7e53118ed0f524adeb0757d25bd2f8e7b0d76dfa714cdd520f7aca8a8b917acc37f51de8f0c9bbe3ad858382e702dc25a12d09f7a858")
    private val serverPub = hex("045f697b6fa1e70a2ebba3788c38c5256b1602e2197c55b6a44601b3fc66a57ea965a92f96a8651d1f59cc5a7db253b64ef5b36bbeaa9015008c43e88bc4b94dec")
    private val key = hex("606fe13a7668cca6e4723797a03f91330d6ca6f5db39eb0795e2e1f4a13bf3cf")

    @Test
    fun `key derivation matches the desktop`() {
        val own = SyncCrypto.privateFromScalar(clientScalar)
        assertArrayEquals(key, SyncCrypto.deriveKey(own, serverPub, "K7QM-2XPA"))
        // The code is typed loosely: case and separators do not matter.
        assertArrayEquals(key, SyncCrypto.deriveKey(own, serverPub, "k7qm2xpa"))
    }

    @Test
    fun `confirmations match the desktop`() {
        val transcript = SyncCrypto.transcript("phone-1", clientPub, serverPub)
        assertArrayEquals(hex("d68fb313ba6bcc89223b6861861c48aec8d5b6d8fe230189a9496ff6ff19650c"),
            SyncCrypto.confirmation(key, "client", transcript))
        assertArrayEquals(hex("524d6c737620815993b7c03e9f1bf60477eece74e8a251039e5a6bfd87831d90"),
            SyncCrypto.confirmation(key, "server", transcript))
    }

    @Test
    fun `opens a message sealed by the desktop`() {
        val blob = hex("aa5b253f02990b159b52e6501bc0c19eb634973902220b1a43ff3e0732509190e90466a66528873eb565c7c17c07e995c48c15bb93a3919a296bba0ba59f4e2ab29fb87afcacd5")
        assertEquals("{\"v\":1,\"hello\":\"Triat\"}", SyncCrypto.open(key, blob, "phone-1"))
    }

    @Test
    fun `round trip, and any change is refused`() {
        val blob = SyncCrypto.seal(key, "{\"v\":1}", "phone-1")
        assertEquals("{\"v\":1}", SyncCrypto.open(key, blob, "phone-1"))
        val tampered = blob.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { SyncCrypto.open(key, tampered, "phone-1") }
        assertThrows(Exception::class.java) { SyncCrypto.open(key, blob, "another-phone") }
    }

    @Test
    fun `bound framing round-trips and the response is tied to the request nonce`() {
        val n = SyncCrypto.newRequestNonce()
        assertEquals(32, n.length)
        val request = SyncCrypto.sealRequest(key, org.json.JSONObject().put("v", 1), "phone-1", n, 1_700_000_000_000L)
        val opened = org.json.JSONObject(SyncCrypto.openWithAad(key, request, SyncCrypto.requestAad("phone-1")))
        assertEquals(n, opened.getString("n"))
        assertEquals(1_700_000_000_000L, opened.getLong("ts"))
        assertEquals(1, opened.getInt("v"))
        // A request does not open as a legacy message, nor as a response.
        assertThrows(Exception::class.java) { SyncCrypto.open(key, request, "phone-1") }
        assertThrows(Exception::class.java) { SyncCrypto.openResponse(key, request, "phone-1", n) }

        val response = SyncCrypto.sealWithAad(key, "{\"v\":1}", SyncCrypto.responseAad("phone-1", n))
        assertEquals("{\"v\":1}", SyncCrypto.openResponse(key, response, "phone-1", n))
        // Another request's nonce, another device, the request AAD, the legacy AAD: all refused.
        assertThrows(Exception::class.java) { SyncCrypto.openResponse(key, response, "phone-1", SyncCrypto.newRequestNonce()) }
        assertThrows(Exception::class.java) { SyncCrypto.openResponse(key, response, "phone-2", n) }
        assertThrows(Exception::class.java) { SyncCrypto.open(key, response, "phone-1") }
        assertThrows(Exception::class.java) { SyncCrypto.openWithAad(key, response, SyncCrypto.requestAad("phone-1")) }
    }

    @Test
    fun `request nonces are unique`() {
        assertEquals(200, (1..200).map { SyncCrypto.newRequestNonce() }.toSet().size)
    }

    @Test
    fun `public keys encode as 65-byte uncompressed points`() {
        val pair = SyncCrypto.newKeyPair()
        val encoded = SyncCrypto.encodePublic(pair.public)
        assertEquals(65, encoded.size)
        assertArrayEquals(encoded, SyncCrypto.encodePublic(SyncCrypto.decodePublic(encoded)))
    }
}
