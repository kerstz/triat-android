package com.ivor.ivormusic.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume

/**
 * A Triat desktop seen on the local network. [host] is ready to drop into a
 * URL: a dotted IPv4 address.
 */
data class FoundDesktop(
    val serverId: String,
    val name: String,
    val host: String,
    val port: Int,
    /** The protocol version it announced; anything but [SyncWire.VERSION] cannot be synced with. */
    val version: Int = SyncWire.VERSION,
    /** Its clock in epoch milliseconds when it said (hello only), else null. */
    val serverNow: Long? = null,
)

/**
 * Finds Triat desktops on the Wi-Fi through mDNS / DNS-SD (`_triat._tcp`).
 * The TXT record carries `id` (the desktop's stable identifier) and `name`;
 * see section 1.1 of docs/sync.md for the protocol.
 */
object DesktopDiscovery {

    const val SERVICE_TYPE = "_triat._tcp."

    private const val TAG = "DesktopDiscovery"

    /** Per attempt; see [discover]. */
    private const val RESOLVE_TIMEOUT_MS = 8_000L
    private const val RESOLVE_ATTEMPTS = 3
    private const val RESOLVE_RETRY_DELAY_MS = 300L

    /**
     * Desktops currently announcing themselves. Every emission is the full
     * list, sorted by name, one entry per `serverId`. Starts with an empty
     * list so a screen has something to draw.
     *
     * Collecting starts the discovery and cancelling the collection stops it.
     * If the platform refuses to start it the flow simply completes: discovery
     * is best effort and the manual `ip:port` entry is the fallback, so there
     * is nothing useful to throw.
     *
     * Resolutions are strictly one at a time: older Android versions fail a
     * second `resolveService` while one is in flight (FAILURE_ALREADY_ACTIVE),
     * and a single consumer draining a queue makes that impossible by
     * construction. A resolution that never calls back is abandoned after
     * [RESOLVE_TIMEOUT_MS] so it cannot block the ones behind it.
     */
    fun discover(context: Context): Flow<List<FoundDesktop>> = callbackFlow {
        val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
        trySend(emptyList())
        if (nsd == null) {
            close()
            awaitClose { }
            return@callbackFlow
        }

        // Callbacks arrive on the platform's NSD thread, the consumer below on
        // the collector's dispatcher: every access to the two collections
        // goes through `lock`.
        val lock = Any()
        val found = LinkedHashMap<String, FoundDesktop>() // keyed by mDNS service name
        val pending = HashSet<String>() // service names queued or being resolved
        val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        fun publish() {
            val snapshot = synchronized(lock) {
                found.values.distinctBy { it.serverId }.sortedBy { it.name.lowercase() }
            }
            trySend(snapshot)
        }

        launch {
            for (service in queue) {
                val desktop = resolve(nsd, service)
                val changed = synchronized(lock) {
                    // Gone from `pending` means the service was lost while it
                    // was resolving: do not resurrect it.
                    val stillWanted = pending.remove(service.serviceName)
                    if (stillWanted && desktop != null) {
                        found[service.serviceName] = desktop
                        true
                    } else {
                        false
                    }
                }
                if (changed) publish()
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: return
                // Not filtered on serviceType: platforms disagree on the
                // trailing dot and we only asked for one type anyway.
                if (synchronized(lock) { pending.add(name) }) queue.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: return
                val removed = synchronized(lock) {
                    pending.remove(name)
                    found.remove(name) != null
                }
                if (removed) publish()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                KLog.w(TAG, "Discovery failed to start (error $errorCode)")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                KLog.w(TAG, "Discovery failed to stop (error $errorCode)")
            }
        }

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: IllegalArgumentException) {
            KLog.w(TAG, "Discovery refused to start", e)
            close()
        } catch (e: SecurityException) {
            KLog.w(TAG, "Discovery not permitted", e)
            close()
        }

        awaitClose {
            queue.close()
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (_: IllegalArgumentException) {
                // The listener was never registered (start failed): nothing to stop.
            }
        }
    }.conflate().distinctUntilChanged()

    private sealed interface Outcome {
        data class Resolved(val info: NsdServiceInfo) : Outcome
        data object Busy : Outcome
        data object Failed : Outcome
    }

    /** Null when the service cannot be resolved, or is not a usable Triat desktop. */
    private suspend fun resolve(nsd: NsdManager, service: NsdServiceInfo): FoundDesktop? {
        repeat(RESOLVE_ATTEMPTS) {
            val outcome = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolveOnce(nsd, service) }
            when (outcome) {
                null, Outcome.Failed -> return null
                is Outcome.Resolved -> return toFound(outcome.info)
                Outcome.Busy -> delay(RESOLVE_RETRY_DELAY_MS)
            }
        }
        return null
    }

    /**
     * `resolveService` is deprecated from API 34 in favour of
     * `registerServiceInfoCallback`, which keeps a service up to date and has
     * to be unregistered. Triat only needs one address per desktop at the
     * moment it is listed, and the old call still works on every version, so
     * it stays. The callback would only pay off if a listed address had to
     * follow a desktop that changes IP while the page is open; the sync
     * repository re-runs the discovery for that case instead.
     */
    @Suppress("DEPRECATION")
    private suspend fun resolveOnce(nsd: NsdManager, service: NsdServiceInfo): Outcome =
        suspendCancellableCoroutine { cont ->
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) {
                        cont.resume(
                            if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) Outcome.Busy
                            else Outcome.Failed,
                        )
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(Outcome.Resolved(serviceInfo))
                }
            }
            try {
                nsd.resolveService(service, listener)
            } catch (e: IllegalArgumentException) {
                KLog.w(TAG, "Resolution refused", e)
                cont.resume(Outcome.Failed)
            }
        }

    private fun toFound(info: NsdServiceInfo): FoundDesktop? =
        desktopFrom(info.serviceName, info.attributes, addressesOf(info), info.port)

    @Suppress("DEPRECATION")
    private fun addressesOf(info: NsdServiceInfo): List<InetAddress> {
        // getHost() is deprecated from API 34, where the full list exists.
        val single = listOfNotNull(info.host)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.ifEmpty { single }
        } else {
            single
        }
    }

    /**
     * Turns a resolved service into a [FoundDesktop]. Pure so it can be tested:
     * a TXT `id` is what marks a Triat desktop, so a service without one is
     * dropped rather than listed under a made-up identity. TXT values are
     * UTF-8 (the desktop's name contains an em dash) and may be absent.
     */
    internal fun desktopFrom(
        serviceName: String?,
        txt: Map<String, ByteArray?>,
        addresses: List<InetAddress>,
        port: Int,
    ): FoundDesktop? {
        val serverId = txt["id"]?.toString(Charsets.UTF_8)?.trim().orEmpty()
        if (serverId.isEmpty() || port !in 1..65535) return null
        val host = pickHost(addresses) ?: return null
        val version = txt["v"]?.toString(Charsets.UTF_8)?.trim()?.toIntOrNull() ?: SyncWire.VERSION
        val name = txt["name"]?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }
            ?: serviceName?.takeIf { it.isNotBlank() }
            ?: host
        return FoundDesktop(serverId = serverId, name = name, host = host, port = port, version = version)
    }

    /**
     * An IPv4 address [isPrivateAddress] accepts (the very predicate the client
     * applies to a host, so nothing is offered that pairing would refuse),
     * RFC1918 ones first (10/8, 172.16/12, 192.168/16): a desktop also announces
     * Docker, VPN or link-local addresses. Loopback and public ones are dropped.
     * IPv6 is dropped: the desktop server binds IPv4 only, so a
     * connection to its IPv6 address is refused.
     */
    internal fun pickHost(addresses: List<InetAddress>): String? {
        val v4 = addresses.filter { it is Inet4Address && isPrivateAddress(it) }
        return (v4.firstOrNull { isRfc1918(it) } ?: v4.firstOrNull())?.hostAddress
    }

    private fun isRfc1918(address: InetAddress): Boolean {
        val b = address.address
        val first = b[0].toInt() and 0xFF
        val second = b[1].toInt() and 0xFF
        return first == 10 || (first == 172 && second in 16..31) || (first == 192 && second == 168)
    }
}
