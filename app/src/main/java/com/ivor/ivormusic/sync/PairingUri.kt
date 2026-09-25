package com.ivor.ivormusic.sync

import java.net.URI
import java.net.URLDecoder

/** What the desktop's pairing QR code carries: `triat://pair?host=..&port=..&code=..&id=..`. */
data class PairingUri(val host: String, val port: Int, val code: String, val serverId: String)

/** Null unless [text] is a well formed Triat pairing address pointing at a private host. */
fun parsePairingUri(text: String): PairingUri? {
    val uri = try { URI(text.trim()) } catch (e: Exception) { return null }
    if (uri.scheme != "triat" || uri.host != "pair") return null
    val query = uri.rawQuery ?: return null
    val params = query.split('&').mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
    }.toMap()
    val host = params["host"]?.takeIf { it.isNotEmpty() } ?: return null
    val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    val code = params["code"]?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val id = params["id"] ?: return null
    if (!isPrivateHost(host)) return null
    return PairingUri(host, port, code, id)
}
