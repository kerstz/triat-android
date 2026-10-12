package com.ivor.ivormusic.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Outcome of [DesktopSyncClient.pair]. */
sealed interface PairResult {
    /** The desktop proved it knows the code too: [desktop] is ready to be saved. */
    data class Paired(val desktop: SyncStore.PairedDesktop) : PairResult

    /** The code is not the one shown on the desktop (or cannot be, by its length). */
    data object WrongCode : PairResult

    /** No code is displayed on the desktop, or it expired or ran out of attempts. */
    data object PairingClosed : PairResult

    /** [reason] is one of the `DesktopSyncClient.REASON_*` codes, for the UI to translate. */
    data class Failed(val reason: String) : PairResult
}

/** Outcome of [DesktopSyncClient.sync]. */
sealed interface SyncResult {
    /** [merged] is the desktop's merged state, to apply as it comes (plus `cookies` when shared). */
    data class Ok(val merged: JSONObject) : SyncResult

    /** The desktop does not know this phone (or the key no longer fits): pair again. */
    data object Unauthorized : SyncResult

    /** [reason] is one of the `DesktopSyncClient.REASON_*` codes, for the UI to translate. */
    data class Failed(val reason: String) : SyncResult
}

/**
 * The phone's side of the Wi-Fi sync with Triat desktop: sections 1.2 to 1.4
 * of docs/sync.md, `triat/core/sync.py` on the desktop. Discovery is
 * [DesktopDiscovery], the stored pairing is [SyncStore], the crypto is
 * [SyncCrypto], the payload is [SyncWire]; this class only speaks HTTP.
 *
 * The transport is plain HTTP, because the desktop has no certificate and the
 * app's network security config has to allow cleartext for it (see
 * `network_security_config.xml`, decided in task A3). That permission covers
 * the whole app, so this client is what keeps it honest, in layers:
 *
 * 1. every entry point refuses a host that [isPrivateHost] does not accept,
 *    before any socket exists, and [urlFor] repeats the check with `require`
 *    as the one door through which a URL is built;
 * 2. a `.local` name is only usable if every address it resolves to is
 *    private too ([PrivateOnlyDns]), so a name cannot lead off the network;
 * 3. redirects are not followed and no proxy is used, so a private host cannot
 *    bounce the request to a public one, and a Wi-Fi proxy setting cannot
 *    carry it out of the network.
 *
 * Nothing here logs a payload, a key, a code or the session cookies.
 *
 * [base] is only used as a starting point for [OkHttpClient.newBuilder]: its
 * interceptors and cache are dropped, since an app-wide interceptor (cookies,
 * user agent) has no business on a LAN exchange.
 */
class DesktopSyncClient(base: OkHttpClient = OkHttpClient()) {

    private val client: OkHttpClient = base.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        // A retry would replay a pairing proof, which the desktop counts as an attempt.
        .retryOnConnectionFailure(false)
        .proxy(Proxy.NO_PROXY)
        .dns(PrivateOnlyDns())
        .cache(null)
        .cookieJar(CookieJar.NO_COOKIES)
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .build()

    /** For [hello], which runs while the user types an address and must answer fast. */
    private val quickClient: OkHttpClient = client.newBuilder()
        .callTimeout(HELLO_CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    /**
     * Asks whatever listens at [host]:[port] who it is (`GET /v1/hello`). Null
     * when the host is refused, does not answer, or is not a Triat desktop. A
     * desktop of another protocol version is returned, with its `version`.
     * Used by the manual `ip:port` entry.
     */
    suspend fun hello(host: String, port: Int): FoundDesktop? = withContext(Dispatchers.IO) {
        if (!isPrivateHost(host) || port !in PORTS) return@withContext null
        val request = Request.Builder().url(urlFor(host, port, "v1/hello")).get().build()
        val reply = try {
            roundTrip(quickClient, request, MAX_SMALL_REPLY)
        } catch (e: IOException) {
            return@withContext null
        }
        if (reply.code != 200) null else parseHello(reply.body, host, port)
    }

    /**
     * Pairs with the desktop at [host]:[port] using the [code] it displays
     * (section 1.3). Nothing is stored here: on [PairResult.Paired] the caller
     * saves the desktop. [deviceId] is `SyncStore.deviceId`, [deviceName] the
     * name the desktop will list this phone under.
     */
    suspend fun pair(
        host: String,
        port: Int,
        code: String,
        deviceId: String,
        deviceName: String,
    ): PairResult = withContext(Dispatchers.IO) {
        if (!isPrivateHost(host)) return@withContext PairResult.Failed(REASON_HOST)
        if (port !in PORTS) return@withContext PairResult.Failed(REASON_PORT)
        if (!isValidDeviceId(deviceId)) return@withContext PairResult.Failed(REASON_DEVICE)
        // A code of the wrong length cannot be right, and the desktop only
        // grants five tries: do not spend one on it.
        if (SyncCrypto.normalizeCode(code).length != CODE_LENGTH) return@withContext PairResult.WrongCode

        val keyPair = SyncCrypto.newKeyPair()
        val phonePub = SyncCrypto.encodePublic(keyPair.public)

        val start = try {
            postJson(
                host, port, "v1/pair/start",
                JSONObject()
                    .put("device_id", deviceId)
                    .put("name", deviceName.take(NAME_LENGTH))
                    .put("pub", SyncCrypto.b64(phonePub)),
            )
        } catch (e: ResponseTooLarge) {
            return@withContext PairResult.Failed(REASON_PROTOCOL)
        } catch (e: IOException) {
            return@withContext PairResult.Failed(REASON_NETWORK)
        }
        when (start.code) {
            200 -> Unit
            409 -> return@withContext PairResult.PairingClosed
            400 -> return@withContext PairResult.Failed(REASON_PROTOCOL)
            else -> return@withContext PairResult.Failed(REASON_SERVER)
        }
        val offer = parsePairStart(start.body) ?: return@withContext PairResult.Failed(REASON_PROTOCOL)

        val key: ByteArray
        val transcript: ByteArray
        val clientProof: ByteArray
        try {
            key = SyncCrypto.deriveKey(keyPair.private, offer.pub, code)
            transcript = SyncCrypto.transcript(deviceId, phonePub, offer.pub)
            clientProof = SyncCrypto.confirmation(key, "client", transcript)
        } catch (e: GeneralSecurityException) {
            // The desktop's public key is not a point of the curve.
            return@withContext PairResult.Failed(REASON_PROTOCOL)
        } catch (e: IllegalArgumentException) {
            return@withContext PairResult.Failed(REASON_PROTOCOL)
        }

        val finish = try {
            postJson(
                host, port, "v1/pair/finish",
                JSONObject().put("device_id", deviceId).put("confirm", SyncCrypto.b64(clientProof)),
            )
        } catch (e: ResponseTooLarge) {
            return@withContext PairResult.Failed(REASON_PROTOCOL)
        } catch (e: IOException) {
            return@withContext PairResult.Failed(REASON_NETWORK)
        }
        when (finish.code) {
            200 -> Unit
            403 -> return@withContext PairResult.WrongCode
            409 -> return@withContext PairResult.PairingClosed
            400 -> return@withContext PairResult.Failed(REASON_PROTOCOL)
            else -> return@withContext PairResult.Failed(REASON_SERVER)
        }

        // The desktop must prove it derived the same key, so a stranger who
        // merely answers 200 does not get recorded as our desktop.
        val serverProof = parseConfirm(finish.body) ?: return@withContext PairResult.Failed(REASON_PROTOCOL)
        val expected = SyncCrypto.confirmation(key, "server", transcript)
        if (!MessageDigest.isEqual(serverProof, expected)) {
            return@withContext PairResult.Failed(REASON_PROOF)
        }
        PairResult.Paired(
            SyncStore.PairedDesktop(
                serverId = offer.serverId,
                name = offer.name.ifEmpty { host },
                host = host,
                port = port,
                key = key,
            ),
        )
    }

    /**
     * Sends the phone's [snapshot] sealed under the pairing key and returns
     * the desktop's merged state (section 1.4). The caller applies it; this
     * function changes nothing on the phone.
     */
    suspend fun sync(
        desktop: SyncStore.PairedDesktop,
        deviceId: String,
        snapshot: JSONObject,
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!isPrivateHost(desktop.host)) return@withContext SyncResult.Failed(REASON_HOST)
        if (desktop.port !in PORTS) return@withContext SyncResult.Failed(REASON_PORT)
        if (!isValidDeviceId(deviceId)) return@withContext SyncResult.Failed(REASON_DEVICE)

        val sealed = try {
            SyncCrypto.seal(desktop.key, snapshot.toString(), deviceId)
        } catch (e: GeneralSecurityException) {
            return@withContext SyncResult.Failed(REASON_KEY)
        } catch (e: IllegalArgumentException) {
            return@withContext SyncResult.Failed(REASON_KEY)
        }
        if (sealed.size > MAX_REQUEST) return@withContext SyncResult.Failed(REASON_TOO_LARGE)

        val request = Request.Builder()
            .url(urlFor(desktop.host, desktop.port, "v1/sync"))
            .header(DEVICE_HEADER, deviceId)
            .post(sealed.toRequestBody(OCTET_STREAM))
            .build()
        val reply = try {
            roundTrip(client, request, MAX_SYNC_REPLY)
        } catch (e: ResponseTooLarge) {
            return@withContext SyncResult.Failed(REASON_PROTOCOL)
        } catch (e: IOException) {
            return@withContext SyncResult.Failed(REASON_NETWORK)
        }
        when (reply.code) {
            200 -> Unit
            401 -> return@withContext SyncResult.Unauthorized
            400 -> return@withContext SyncResult.Failed(REASON_VERSION)
            else -> return@withContext SyncResult.Failed(REASON_SERVER)
        }

        val merged = try {
            JSONObject(SyncCrypto.open(desktop.key, reply.body, deviceId))
        } catch (e: GeneralSecurityException) {
            // Altered, or sealed under another key.
            return@withContext SyncResult.Failed(REASON_PROTOCOL)
        } catch (e: IOException) {
            // Not gzip once decrypted.
            return@withContext SyncResult.Failed(REASON_PROTOCOL)
        } catch (e: JSONException) {
            return@withContext SyncResult.Failed(REASON_PROTOCOL)
        } catch (e: IllegalArgumentException) {
            return@withContext SyncResult.Failed(REASON_PROTOCOL)
        }
        if (merged.optInt("v", SyncWire.VERSION) != SyncWire.VERSION) {
            return@withContext SyncResult.Failed(REASON_VERSION)
        }
        SyncResult.Ok(merged)
    }

    // ---- transport ------------------------------------------------------

    private suspend fun postJson(host: String, port: Int, path: String, body: JSONObject): Reply {
        val request = Request.Builder()
            .url(urlFor(host, port, path))
            .post(body.toString().toRequestBody(JSON))
            .build()
        return roundTrip(client, request, MAX_SMALL_REPLY)
    }

    private class Reply(val code: Int, val body: ByteArray)

    private class ResponseTooLarge : IOException("response too large")

    /**
     * One request, its status and its body read in full (up to [maxBody]).
     * Cancelling the coroutine cancels the call, even while the body is being
     * read: the blocking work happens on OkHttp's own threads, not on ours.
     */
    private suspend fun roundTrip(http: OkHttpClient, request: Request, maxBody: Long): Reply =
        suspendCancellableCoroutine { cont ->
            val call = http.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val reply = try {
                        response.use { r ->
                            Reply(r.code, readBounded(r.body ?: throw IOException("no body"), maxBody))
                        }
                    } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                        return
                    }
                    if (cont.isActive) cont.resume(reply)
                }
            })
        }

    private fun readBounded(body: ResponseBody, max: Long): ByteArray {
        if (body.contentLength() > max) throw ResponseTooLarge()
        val source = body.source()
        source.request(max + 1)
        if (source.buffer.size > max) throw ResponseTooLarge()
        return source.readByteArray()
    }

    companion object {
        /** Reasons carried by [PairResult.Failed] and [SyncResult.Failed]: short codes, not sentences. */
        const val REASON_HOST = "host"          // the address is not a private one, nothing was sent
        const val REASON_PORT = "port"
        const val REASON_DEVICE = "device"      // this phone's id cannot travel in a header
        const val REASON_NETWORK = "network"    // unreachable, refused, timed out, cut
        const val REASON_SERVER = "server"      // an HTTP status the protocol does not use
        const val REASON_PROTOCOL = "protocol"  // an answer that is not a Triat desktop's
        const val REASON_PROOF = "proof"        // the desktop's proof of the code does not match
        const val REASON_VERSION = "version"    // the desktop speaks another protocol version
        const val REASON_KEY = "key"            // the stored key cannot seal anything
        const val REASON_TOO_LARGE = "too_large"

        private const val CONNECT_TIMEOUT_S = 3L
        private const val READ_TIMEOUT_S = 20L
        private const val CALL_TIMEOUT_S = 60L
        private const val HELLO_CALL_TIMEOUT_S = 8L

        private const val CODE_LENGTH = 8
        private const val NAME_LENGTH = 60
        private const val DEVICE_ID_MAX = 64
        private const val DEVICE_HEADER = "X-Triat-Device"

        /** The desktop refuses request bodies over 16 MiB. */
        private const val MAX_REQUEST = 16L * 1024 * 1024
        private const val MAX_SYNC_REPLY = 32L * 1024 * 1024
        private const val MAX_SMALL_REPLY = 64L * 1024

        private val PORTS = 1..65535
        private val JSON = "application/json".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        /**
         * The single place a URL is built. The public entry points have
         * already answered a refused host with [REASON_HOST]; the `require`
         * here is the guard that must never be removed, so that a future entry
         * point which forgets the check fails loudly instead of sending sync
         * traffic to an address that is not on the local network.
         */
        internal fun urlFor(host: String, port: Int, path: String): HttpUrl {
            require(isPrivateHost(host)) { "refusing a host that is not on the local network" }
            require(port in PORTS) { "bad port" }
            return HttpUrl.Builder()
                .scheme("http")
                .host(host.removeSurrounding("[", "]"))
                .port(port)
                .addPathSegments(path)
                .build()
        }

        /** The device id goes into a header and into the desktop's 64-character field. */
        internal fun isValidDeviceId(id: String): Boolean =
            id.length in 1..DEVICE_ID_MAX && id.all { it.code in 0x21..0x7E }

        /** The desktop's `/v1/hello` answer as a [FoundDesktop], or null when it is not one. */
        internal fun parseHello(body: ByteArray, host: String, port: Int): FoundDesktop? {
            val o = parseObject(body) ?: return null
            if (text(o, "app") != "triat" || !o.has("v")) return null
            val serverId = text(o, "server_id").trim()
            if (serverId.isEmpty()) return null
            // A version other than ours is still a Triat desktop: reported as
            // such (FoundDesktop.version) so the UI says "incompatible".
            val now = if (o.has("now")) o.optLong("now", 0L).takeIf { it > 0 } else null
            return FoundDesktop(
                serverId, text(o, "name").trim().ifEmpty { host }, host, port,
                version = o.optInt("v", 0), serverNow = now,
            )
        }

        internal class PairOffer(val serverId: String, val name: String, val pub: ByteArray)

        /** The `/v1/pair/start` answer, or null when it is not usable. */
        internal fun parsePairStart(body: ByteArray): PairOffer? {
            val o = parseObject(body) ?: return null
            val serverId = text(o, "server_id").trim()
            val pub = unb64OrNull(text(o, "pub")) ?: return null
            if (serverId.isEmpty() || pub.size != 65 || pub[0] != 0x04.toByte()) return null
            return PairOffer(serverId, text(o, "name").trim(), pub)
        }

        /** The `/v1/pair/finish` answer's proof, or null when it is not there. */
        internal fun parseConfirm(body: ByteArray): ByteArray? {
            val o = parseObject(body) ?: return null
            return unb64OrNull(text(o, "confirm"))?.takeIf { it.isNotEmpty() }
        }

        private fun parseObject(body: ByteArray): JSONObject? = try {
            JSONObject(String(body, Charsets.UTF_8))
        } catch (e: JSONException) {
            null
        }

        /** A string field, or "" when absent, null or not a string (org.json would print "null"). */
        private fun text(o: JSONObject, key: String): String = o.opt(key) as? String ?: ""

        private fun unb64OrNull(text: String): ByteArray? =
            try {
                SyncCrypto.unb64(text)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}

/**
 * Whether [host] is an address the sync may talk to: on this phone or on the
 * local network, never on the internet. Pure and strict on purpose: what it
 * checks must be what the socket connects to, so anything ambiguous is refused.
 *
 * Accepted:
 * - IPv4 in dotted decimal: `10/8`, `172.16/12`, `192.168/16`, `169.254/16`
 *   (link-local) and `127/8` (loopback);
 * - IPv6 `fe80::/10` (link-local), `fc00::/7` (unique local) and `::1`, bare
 *   or in brackets, and an IPv4-mapped IPv6 address whose IPv4 part is one of
 *   the above;
 * - a `*.local` name (mDNS), which [PrivateOnlyDns] then requires to resolve
 *   to private addresses only.
 *
 * Refused: every other address (including `0.0.0.0`, `100.64/10`), every other
 * name (`localhost` and public domains alike), IPv6 with a zone (`%wlan0`, it
 * cannot be put in a URL), short or padded IPv4 forms (`127.1`, `010.0.0.1`,
 * a single big number), anything with a port, a path, a user or a space in it.
 */
fun isPrivateHost(host: String): Boolean {
    if (host.isEmpty() || host.length > 255) return false
    if (host.startsWith("[") || host.contains(':')) {
        val literal = if (host.startsWith("[")) {
            if (!host.endsWith("]")) return false
            host.substring(1, host.length - 1)
        } else {
            host
        }
        return isPrivateIpv6Literal(literal)
    }
    parseIpv4(host)?.let { return isPrivateIpv4(it[0], it[1]) }
    // Only digits and dots but not a strict dotted quad: an alternative
    // spelling of some address ("127.1", "2130706433"), never a name.
    if (host.all { it in '0'..'9' || it == '.' }) return false
    return isLocalName(host)
}

/** The four octets of a strict dotted-decimal IPv4 address, or null. */
private fun parseIpv4(text: String): IntArray? {
    val parts = text.split('.')
    if (parts.size != 4) return null
    val octets = IntArray(4)
    for ((i, part) in parts.withIndex()) {
        if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
        if (part.length > 1 && part[0] == '0') return null
        octets[i] = part.toInt()
        if (octets[i] > 255) return null
    }
    return octets
}

private fun isPrivateIpv4(first: Int, second: Int): Boolean =
    first == 10 ||
        first == 127 ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168) ||
        (first == 169 && second == 254)

private fun isPrivateIpv6Literal(text: String): Boolean {
    if (':' !in text) return false
    // Hex, colons and dots only (an embedded IPv4 tail): this is what keeps
    // getByName from ever being handed something it would look up by name.
    if (!text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }) return false
    val address = try {
        InetAddress.getByName(text)
    } catch (e: UnknownHostException) {
        return false
    }
    return isPrivateAddress(address)
}

/** The address-level twin of [isPrivateHost], for what a name resolved to. */
internal fun isPrivateAddress(address: InetAddress): Boolean {
    val bytes = address.address
    return when (address) {
        is Inet4Address -> isPrivateIpv4(bytes[0].toInt() and 0xFF, bytes[1].toInt() and 0xFF)
        is Inet6Address -> address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            (bytes[0].toInt() and 0xFE) == 0xFC
        else -> false
    }
}

/** `label.local`: at least one label before `.local`, each a plain hostname label. */
private fun isLocalName(host: String): Boolean {
    if (host.length > 253) return false
    val labels = host.split('.')
    // Every label is checked as plain ASCII, the last one included, so case
    // folding can never turn some other character into "local".
    if (labels.size < 2 || !labels.last().equals("local", ignoreCase = true)) return false
    return labels.all { label ->
        label.length in 1..63 &&
            label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' } &&
            label.first() != '-' && label.last() != '-'
    }
}

/**
 * Name resolution that only ever returns private addresses. OkHttp does not
 * consult a [Dns] for an IP literal (the string check covers those), only for
 * names, and the only names [isPrivateHost] lets through are `*.local`: if one
 * resolves, even partly, to a public address, the whole answer is refused.
 */
internal class PrivateOnlyDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (!isPrivateHost(hostname)) throw UnknownHostException("$hostname is not a local host")
        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty() || !addresses.all(::isPrivateAddress)) {
            throw UnknownHostException("$hostname does not resolve to a local address")
        }
        return addresses
    }
}
