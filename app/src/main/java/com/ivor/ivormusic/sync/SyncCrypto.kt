package com.ivor.ivormusic.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto of the Wi-Fi sync with Triat desktop, byte-compatible with the
 * desktop's `triat/core/sync.py`.
 *
 * Pairing: ECDH on P-256, the shared secret run through HKDF-SHA256 with
 * the SHA-256 of the 8-character code as salt, so the key depends on both
 * the exchange and the code shown on the desktop. The phone proves it knows
 * the code first (HMAC "client"), the desktop answers with its own proof
 * (HMAC "server"). After that every message is AES-256-GCM over gzipped
 * JSON, with the device id as associated data.
 *
 * Pure JVM on purpose: the interop test runs against vectors produced by
 * the Python side, without a device.
 */
object SyncCrypto {

    private const val INFO = "triat-sync-v1"
    private val random = SecureRandom()

    private val p256: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
    }

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), random)
    }.generateKeyPair()

    /** X9.62 uncompressed point, 65 bytes, as the desktop expects. */
    fun encodePublic(key: PublicKey): ByteArray {
        val point = (key as ECPublicKey).w
        return byteArrayOf(0x04) + fixed32(point.affineX) + fixed32(point.affineY)
    }

    fun decodePublic(bytes: ByteArray): PublicKey {
        require(bytes.size == 65 && bytes[0] == 0x04.toByte()) { "bad public key" }
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val y = BigInteger(1, bytes.copyOfRange(33, 65))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), p256))
    }

    /** For tests: a private key from its scalar. */
    fun privateFromScalar(scalar: BigInteger): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(scalar, p256))

    fun normalizeCode(code: String): String =
        code.uppercase().filter { it.isLetterOrDigit() && it.code < 128 }

    fun deriveKey(own: PrivateKey, peerPublic: ByteArray, code: String): ByteArray {
        val shared = KeyAgreement.getInstance("ECDH").run {
            init(own)
            doPhase(decodePublic(peerPublic), true)
            generateSecret()
        }
        val salt = MessageDigest.getInstance("SHA-256")
            .digest(normalizeCode(code).toByteArray(Charsets.US_ASCII))
        return hkdf(shared, salt, INFO.toByteArray(), 32)
    }

    fun transcript(deviceId: String, clientPub: ByteArray, serverPub: ByteArray): ByteArray =
        deviceId.toByteArray() + '|'.code.toByte() + clientPub + '|'.code.toByte() + serverPub

    fun confirmation(key: ByteArray, role: String, transcript: ByteArray): ByteArray =
        hmac(key, role.toByteArray() + '|'.code.toByte() + transcript)

    fun seal(key: ByteArray, json: String, deviceId: String): ByteArray {
        val nonce = ByteArray(12).also(random::nextBytes)
        return nonce + aes(Cipher.ENCRYPT_MODE, key, nonce, deviceId, gzip(json.toByteArray()))
    }

    /** Throws on a wrong key or any altered byte (GCM tag). */
    fun open(key: ByteArray, blob: ByteArray, deviceId: String): String {
        require(blob.size > 28) { "message too short" }
        val nonce = blob.copyOfRange(0, 12)
        val plain = aes(Cipher.DECRYPT_MODE, key, nonce, deviceId, blob.copyOfRange(12, blob.size))
        return String(gunzip(plain))
    }

    // ---- primitives ----------------------------------------------------

    private fun aes(mode: Int, key: ByteArray, nonce: ByteArray, deviceId: String,
                    data: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(deviceId.toByteArray())
            doFinal(data)
        }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    /** RFC 5869, the same construction as `cryptography`'s HKDF. */
    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmac(salt, ikm)
        val out = ByteArrayOutputStream()
        var block = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            block = hmac(prk, block + info + counter.toByte())
            out.write(block)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }

    private fun fixed32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }
            .toByteArray()

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

    fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)
}
