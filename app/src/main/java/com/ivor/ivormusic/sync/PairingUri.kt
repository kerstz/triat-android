package com.ivor.ivormusic.sync

import java.net.URI
import java.net.URLDecoder

/**
 * What the desktop's pairing QR code carries:
 * `triat://pair?host=..&host=..&port=..&code=..&id=..`. [hosts] are the
 * private addresses of the desktop in the order it listed them (the first is
 * its default-route address); there is always at least one.
 */
data class PairingUri(val hosts: List<String>, val port: Int, val code: String, val serverId: String) {
    /** The address an older single-host code carried, and the first to try. */
    val host: String get() = hosts.first()
}

/**
 * Null unless [text] is a well formed Triat pairing address with at least one
 * private host. Hosts that are not private are dropped, not fatal: a code may
 * list a VPN or public address next to the LAN one.
 */
fun parsePairingUri(text: String): PairingUri? {
    val uri = try { URI(text.trim()) } catch (e: Exception) { return null }
    if (uri.scheme != "triat" || uri.host != "pair") return null
    val query = uri.rawQuery ?: return null
    val params = query.split('&').mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
    }
    fun first(name: String) = params.firstOrNull { it.first == name }?.second
    val hosts = params.filter { it.first == "host" }.map { it.second }
        .filter { it.isNotEmpty() && isPrivateHost(it) }
        .distinct()
    if (hosts.isEmpty()) return null
    val port = first("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    val code = first("code")?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val id = first("id") ?: return null
    return PairingUri(hosts, port, code, id)
}

/**
 * The desktop a scanned code points at, among what answered at its hosts
 * ([answers], in order, null for a host that did not answer) and what mDNS
 * lists ([discovered]). Only a desktop whose `serverId` is the code's counts:
 * the code names who it is for.
 */
internal fun matchQrDesktop(
    qr: PairingUri,
    answers: List<FoundDesktop?>,
    discovered: List<FoundDesktop>,
): FoundDesktop? =
    answers.firstOrNull { it != null && it.serverId == qr.serverId }
        ?: discovered.firstOrNull { it.serverId == qr.serverId }
