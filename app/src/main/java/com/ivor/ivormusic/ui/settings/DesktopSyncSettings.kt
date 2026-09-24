package com.ivor.ivormusic.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivor.ivormusic.R
import com.ivor.ivormusic.sync.DesktopSyncClient
import com.ivor.ivormusic.sync.DesktopSyncRepository
import com.ivor.ivormusic.sync.FoundDesktop
import com.ivor.ivormusic.sync.PairResult
import com.ivor.ivormusic.sync.SyncCrypto
import com.ivor.ivormusic.sync.SyncUiState
import com.ivor.ivormusic.sync.isPrivateHost
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Pure logic. Everything up to the view model is free of Android and Compose so
// that the wording decisions can be held to their rules by DesktopSyncSettingsTest.
// ---------------------------------------------------------------------------

/** The desktop shows its pairing code as 8 characters, grouped 4 + 4. */
internal const val PAIRING_CODE_LENGTH = 8

/** The port the desktop listens on unless it was busy; the manual entry falls back to it. */
internal const val DEFAULT_SYNC_PORT = 47823

/** How long the page looks for a desktop before it offers the manual address. */
private const val DISCOVERY_PATIENCE_MS = 5_000L

/** How often the "synced X ago" texts are recomputed while they are on screen. */
private const val CLOCK_TICK_MS = 30_000L

/**
 * What the code field holds after the user typed or pasted [raw]: upper case,
 * dashes, spaces and anything that is not an ASCII letter or digit dropped,
 * cut to [PAIRING_CODE_LENGTH]. The same rule the client applies before it
 * derives the key ([SyncCrypto.normalizeCode]), so what the field shows is
 * what is sent.
 */
internal fun normalizeCodeInput(raw: String): String =
    SyncCrypto.normalizeCode(raw).take(PAIRING_CODE_LENGTH)

/** A host and port typed by hand. [host] is ready for a URL (IPv6 in brackets). */
internal data class ManualAddress(val host: String, val port: Int)

/**
 * Reads the fallback `address:port` entry: `192.168.1.20:47823`, `pc.local`,
 * `[fe80::1]:5000`, a bare IPv6 literal, optionally pasted with `http://` and a
 * trailing slash. Without a port the default one is assumed. Null when the text
 * is not an address, the port is not 1..65535, or the host is not one the sync
 * may talk to ([isPrivateHost]: local network only, never the internet), so the
 * screen can say so before anything is sent.
 */
internal fun parseManualAddress(input: String): ManualAddress? {
    var text = input.trim()
    if (text.startsWith("http://", ignoreCase = true)) text = text.substring("http://".length)
    text = text.trimEnd('/')
    if (text.isEmpty() || text.any { it.isWhitespace() || it == '/' || it == '@' || it == '?' || it == '#' }) return null

    val host: String
    val portText: String?
    val colons = text.count { it == ':' }
    when {
        text.startsWith("[") -> {
            val close = text.indexOf(']')
            if (close < 0) return null
            host = text.substring(0, close + 1)
            val rest = text.substring(close + 1)
            portText = when {
                rest.isEmpty() -> null
                rest.startsWith(":") -> rest.substring(1)
                else -> return null
            }
        }
        colons == 0 -> { host = text; portText = null }
        colons == 1 -> { host = text.substringBefore(':'); portText = text.substringAfter(':') }
        // Several colons and no brackets: an IPv6 literal without a port.
        else -> { host = "[$text]"; portText = null }
    }
    val port = if (portText == null) {
        DEFAULT_SYNC_PORT
    } else {
        portText.takeIf { it.length in 1..5 && it.all { c -> c in '0'..'9' } }
            ?.toInt()?.takeIf { it in 1..65535 } ?: return null
    }
    if (!isPrivateHost(host)) return null
    return ManualAddress(host, port)
}

/**
 * How long ago the last sync was, in the buckets the screen words. Rounded down:
 * "1 min" means at least one minute. A clock that went backwards reads as just now.
 */
internal sealed interface SyncAge {
    data object Never : SyncAge
    data object JustNow : SyncAge
    data class Minutes(val n: Int) : SyncAge
    data class Hours(val n: Int) : SyncAge
    data class Days(val n: Int) : SyncAge
}

internal fun syncAge(lastSyncAt: Long, now: Long): SyncAge {
    if (lastSyncAt <= 0L) return SyncAge.Never
    val elapsed = now - lastSyncAt
    return when {
        elapsed < 60_000L -> SyncAge.JustNow
        elapsed < 3_600_000L -> SyncAge.Minutes((elapsed / 60_000L).toInt())
        elapsed < 86_400_000L -> SyncAge.Hours((elapsed / 3_600_000L).toInt())
        else -> SyncAge.Days(minOf(elapsed / 86_400_000L, 9_999L).toInt())
    }
}

/** What went wrong, from the user's side. Each one has its own readable message. */
internal enum class SyncProblem {
    /** The code does not match. */
    WRONG_CODE,

    /** The desktop is not waiting for a code: it expired, was tried too often, or was never asked. */
    CODE_EXPIRED,

    /** Nothing that speaks Triat answered at the address that was typed. */
    DESKTOP_NOT_FOUND,

    /** The connection failed: off, other Wi-Fi, firewall. */
    UNREACHABLE,

    /** The desktop answered that it does not know this phone (it forgot it). */
    FORGOTTEN,

    /** The address that was typed is not a local network address. */
    BAD_ADDRESS,

    /** The thing at that address does not answer like a Triat desktop. */
    NOT_TRIAT,

    /** The desktop could not prove it derived the same key. */
    UNVERIFIED,

    /** The desktop speaks another protocol version. */
    INCOMPATIBLE,

    /** The stored key cannot be used any more. */
    DAMAGED_PAIRING,

    /** The library is too big for one exchange. */
    TOO_LARGE,

    /** Something on the phone itself failed. */
    INTERNAL;

    /** Whether the only way out is to pair again, so the message comes with that button. */
    val needsNewPairing: Boolean get() = this == FORGOTTEN || this == DAMAGED_PAIRING
}

/**
 * The problem behind a short error code: [SyncUiState.lastError], or the
 * `reason` of a failed pairing or sync. An unknown code reads as [SyncProblem.INTERNAL]
 * rather than being shown raw.
 */
internal fun problemFor(reason: String): SyncProblem = when (reason) {
    DesktopSyncRepository.ERROR_UNPAIRED -> SyncProblem.FORGOTTEN
    DesktopSyncClient.REASON_NETWORK -> SyncProblem.UNREACHABLE
    DesktopSyncClient.REASON_HOST, DesktopSyncClient.REASON_PORT -> SyncProblem.BAD_ADDRESS
    DesktopSyncClient.REASON_SERVER, DesktopSyncClient.REASON_PROTOCOL -> SyncProblem.NOT_TRIAT
    DesktopSyncClient.REASON_PROOF -> SyncProblem.UNVERIFIED
    DesktopSyncClient.REASON_VERSION -> SyncProblem.INCOMPATIBLE
    DesktopSyncClient.REASON_KEY -> SyncProblem.DAMAGED_PAIRING
    DesktopSyncClient.REASON_TOO_LARGE -> SyncProblem.TOO_LARGE
    // ERROR_INTERNAL, REASON_DEVICE (this phone's own id), ERROR_NOT_PAIRED and anything new.
    else -> SyncProblem.INTERNAL
}

/** What a pairing attempt failed on, or null when it worked. */
internal fun problemFor(result: PairResult): SyncProblem? = when (result) {
    is PairResult.Paired -> null
    PairResult.WrongCode -> SyncProblem.WRONG_CODE
    PairResult.PairingClosed -> SyncProblem.CODE_EXPIRED
    is PairResult.Failed -> problemFor(result.reason)
}

/** The one-line state of the sync, for the hub row and the header of the page. */
internal sealed interface SyncStatus {
    data object NotPaired : SyncStatus
    data object Syncing : SyncStatus
    data class Problem(val problem: SyncProblem) : SyncStatus
    data class Synced(val age: SyncAge) : SyncStatus
}

internal fun syncStatus(state: SyncUiState, now: Long): SyncStatus = when {
    state.paired == null -> SyncStatus.NotPaired
    state.busy -> SyncStatus.Syncing
    state.lastError != null -> SyncStatus.Problem(problemFor(state.lastError))
    else -> SyncStatus.Synced(syncAge(state.lastSyncAt, now))
}

// ---------------------------------------------------------------------------
// View model
// ---------------------------------------------------------------------------

/** The pairing attempt in flight, and how the last one failed. Not kept in the repository: it is this screen's. */
internal data class PairingUi(val busy: Boolean = false, val problem: SyncProblem? = null)

internal class DesktopSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DesktopSyncRepository.get(application)
    val state: StateFlow<SyncUiState> = repository.state

    private val mutablePairing = MutableStateFlow(PairingUi())
    val pairing: StateFlow<PairingUi> = mutablePairing.asStateFlow()

    /** Listing the desktops on the Wi-Fi is only worth its cost while the page is looking at them. */
    fun startDiscovery() = repository.startDiscovery(viewModelScope)

    fun stopDiscovery() = repository.stopDiscovery()

    fun pair(found: FoundDesktop, code: String) = runPairing { problemFor(repository.pair(found, code)) }

    /** The manual entry: checks the address, asks who answers there, then pairs with it. */
    fun pairAt(address: String, code: String) = runPairing {
        val target = parseManualAddress(address) ?: return@runPairing SyncProblem.BAD_ADDRESS
        val found = repository.lookup(target.host, target.port) ?: return@runPairing SyncProblem.DESKTOP_NOT_FOUND
        problemFor(repository.pair(found, code))
    }

    fun dismissProblem() {
        if (mutablePairing.value.problem != null) mutablePairing.value = PairingUi()
    }

    /**
     * Not cancelled when the page is left: a sync stopped between two of its
     * writes would leave the playlists half applied until the next one. The
     * repository already serialises them and the client bounds the wait.
     */
    fun syncNow() {
        viewModelScope.launch { withContext(NonCancellable) { repository.syncNow() } }
    }

    fun setShareSession(accepted: Boolean) = repository.setShareSessionAccepted(accepted)

    fun forget() {
        repository.forget()
        mutablePairing.value = PairingUi()
    }

    private fun runPairing(attempt: suspend () -> SyncProblem?) {
        if (mutablePairing.value.busy) return
        mutablePairing.value = PairingUi(busy = true)
        viewModelScope.launch {
            val problem = try {
                attempt()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e(TAG, "Pairing failed unexpectedly", e)
                SyncProblem.INTERNAL
            }
            mutablePairing.value = PairingUi(problem = problem)
        }
    }

    private companion object {
        const val TAG = "DesktopSyncViewModel"
    }
}

// ---------------------------------------------------------------------------
// Hub row
// ---------------------------------------------------------------------------

@Composable
internal fun DesktopSyncHubRow(onClick: () -> Unit, model: DesktopSyncViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val now = rememberClock(active = state.paired != null)
    val status = syncStatus(state, now)
    val value = if (status is SyncStatus.NotPaired) {
        stringResource(R.string.ds_not_paired)
    } else {
        stringResource(R.string.ds_hub_value, state.paired?.name.orEmpty(), hubStatusText(status))
    }
    SettingsHubRow(
        icon = Icons.Rounded.Computer, title = stringResource(R.string.ds_title),
        value = value, onClick = onClick, explanation = stringResource(R.string.ds_intro)
    )
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

@Composable
internal fun DesktopSyncSettingsPage(onBack: () -> Unit, model: DesktopSyncViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val pairing by model.pairing.collectAsStateWithLifecycle()
    val paired = state.paired
    val now = rememberClock(active = paired != null)
    val focus = LocalFocusManager.current

    // The code is a secret of a couple of minutes: kept in memory only, never in saved state.
    var code by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FoundDesktop?>(null) }
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var waited by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }

    // Listen for desktops only while the page is on screen and there is nobody paired yet.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, paired == null) {
        if (paired != null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.startDiscovery()
                Lifecycle.Event.ON_STOP -> model.stopDiscovery()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            model.stopDiscovery()
        }
    }

    // Nothing announced itself after a few seconds: say what to check and open the manual entry
    // (once: coming back to the page starts the wait over but never re-opens what the user closed).
    LaunchedEffect(lifecycleOwner, paired == null) {
        waited = false
        if (paired == null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                waited = false
                delay(DISCOVERY_PATIENCE_MS)
                waited = true
                if (state.found.isEmpty()) manualOpen = true
            }
        }
    }
    // Paired (here or by an earlier sync): the form has done its job.
    LaunchedEffect(paired != null) {
        if (paired != null) {
            code = ""; selected = null; manualOpen = false; address = ""
        }
    }
    // A desktop that stopped listening does not vanish from under the user's finger, but a new address wins.
    val target = state.found.firstOrNull { it.serverId == selected?.serverId } ?: selected

    Box(Modifier.fillMaxSize().imePadding()) {
        SettingsDetailScaffold(title = stringResource(R.string.ds_title), onBack = onBack) {
            if (paired != null) {
                item(key = "header") {
                    DesktopSyncHeader(name = paired.name, busy = state.busy, age = syncAge(state.lastSyncAt, now), changes = state.lastChanges)
                }
                state.lastError?.let { error ->
                    val problem = problemFor(error)
                    item(key = "problem") {
                        DesktopSyncNotice(problem, onRepair = if (problem.needsNewPairing) model::forget else null)
                    }
                }
                item(key = "sync") {
                    Button(
                        onClick = model::syncNow, enabled = !state.busy, shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        if (state.busy) LoadingIndicator(Modifier.size(24.dp))
                        else Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (state.busy) R.string.ds_syncing else R.string.ds_sync_now))
                    }
                }
                item(key = "session") {
                    SettingsCard {
                        SettingsToggleRow(
                            icon = Icons.Rounded.AccountCircle, title = stringResource(R.string.ds_share_session_title),
                            subtitle = stringResource(R.string.ds_share_session_sub),
                            enabled = state.shareSession, onToggle = model::setShareSession
                        )
                    }
                }
                item(key = "what") {
                    SettingsSection(title = stringResource(R.string.ds_what_syncs_title)) {
                        SettingsCard {
                            Text(
                                stringResource(R.string.ds_what_syncs_body), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                item(key = "forget") {
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.LinkOff, title = stringResource(R.string.ds_forget_title),
                            subtitle = stringResource(R.string.ds_forget_sub), onClick = { confirmForget = true },
                            tint = SettingsRowDefaults.destructiveTint, titleColor = SettingsRowDefaults.destructiveTint
                        )
                    }
                }
            } else {
                item(key = "intro") { DesktopSyncIntro() }
                pairing.problem?.let { problem -> item(key = "problem") { DesktopSyncNotice(problem) } }

                if (!manualOpen) {
                    if (state.found.isNotEmpty()) {
                        item(key = "found") {
                            SettingsSection(title = stringResource(R.string.ds_found_title)) {
                                SettingsCard {
                                    state.found.forEachIndexed { index, desktop ->
                                        if (index > 0) SettingsDivider()
                                        FoundDesktopRow(
                                            desktop, selected = desktop.serverId == target?.serverId, enabled = !pairing.busy,
                                            onClick = { selected = desktop; model.dismissProblem() }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (waited) {
                        item(key = "nothing") { NothingFoundCard() }
                    } else {
                        item(key = "searching") { SearchingCard() }
                    }
                    target?.let { chosen ->
                        item(key = "code") {
                            PairCodeCard(
                                title = stringResource(R.string.ds_pair_with, chosen.name),
                                address = null, onAddress = {}, code = code,
                                onCode = { code = normalizeCodeInput(it); model.dismissProblem() },
                                busy = pairing.busy, canPair = code.length == PAIRING_CODE_LENGTH,
                                onPair = { focus.clearFocus(); model.pair(chosen, code) }
                            )
                        }
                    }
                    item(key = "manual-toggle") {
                        TextButton(onClick = { manualOpen = true; selected = null; model.dismissProblem() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.ds_manual_open))
                        }
                    }
                } else {
                    if (state.found.isEmpty() && waited) item(key = "nothing") { NothingFoundCard() }
                    item(key = "code") {
                        PairCodeCard(
                            title = stringResource(R.string.ds_manual_title),
                            address = address, onAddress = { address = it; model.dismissProblem() }, code = code,
                            onCode = { code = normalizeCodeInput(it); model.dismissProblem() },
                            busy = pairing.busy, canPair = code.length == PAIRING_CODE_LENGTH && address.isNotBlank(),
                            onPair = { focus.clearFocus(); model.pairAt(address, code) }
                        )
                    }
                    if (state.found.isNotEmpty()) item(key = "manual-toggle") {
                        TextButton(onClick = { manualOpen = false; model.dismissProblem() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.ds_manual_close))
                        }
                    }
                }
            }
        }

        if (confirmForget && paired != null) {
            AlertDialog(
                onDismissRequest = { confirmForget = false },
                title = { Text(stringResource(R.string.ds_forget_confirm_title, paired.name)) },
                text = { Text(stringResource(R.string.ds_forget_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = { confirmForget = false; model.forget() }) {
                        Text(stringResource(R.string.ds_forget_action), color = SettingsRowDefaults.destructiveTint)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.action_cancel)) }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(32.dp)
            )
        }
    }
}

/** The current time, refreshed while [active], so that "5 min ago" keeps moving on a screen left open. */
@Composable
private fun rememberClock(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        if (active) {
            now = System.currentTimeMillis()
            while (true) {
                delay(CLOCK_TICK_MS)
                now = System.currentTimeMillis()
            }
        }
    }
    return now
}

@Composable
private fun ageText(age: SyncAge): String = when (age) {
    SyncAge.Never -> stringResource(R.string.ds_never_synced)
    SyncAge.JustNow -> stringResource(R.string.ds_synced_just_now)
    is SyncAge.Minutes -> pluralStringResource(R.plurals.ds_synced_minutes_ago, age.n, age.n)
    is SyncAge.Hours -> pluralStringResource(R.plurals.ds_synced_hours_ago, age.n, age.n)
    is SyncAge.Days -> pluralStringResource(R.plurals.ds_synced_days_ago, age.n, age.n)
}

/** The status without the desktop's name, for the hub row. The full message of a problem is on the page. */
@Composable
private fun hubStatusText(status: SyncStatus): String = when (status) {
    SyncStatus.NotPaired -> stringResource(R.string.ds_not_paired)
    SyncStatus.Syncing -> stringResource(R.string.ds_syncing)
    is SyncStatus.Problem -> stringResource(R.string.ds_hub_problem)
    is SyncStatus.Synced -> ageText(status.age)
}

@Composable
private fun problemText(problem: SyncProblem): String = stringResource(
    when (problem) {
        SyncProblem.WRONG_CODE -> R.string.ds_error_wrong_code
        SyncProblem.CODE_EXPIRED -> R.string.ds_error_code_expired
        SyncProblem.DESKTOP_NOT_FOUND -> R.string.ds_error_not_found
        SyncProblem.UNREACHABLE -> R.string.ds_error_unreachable
        SyncProblem.FORGOTTEN -> R.string.ds_error_forgotten
        SyncProblem.BAD_ADDRESS -> R.string.ds_error_bad_address
        SyncProblem.NOT_TRIAT -> R.string.ds_error_not_triat
        SyncProblem.UNVERIFIED -> R.string.ds_error_unverified
        SyncProblem.INCOMPATIBLE -> R.string.ds_error_incompatible
        SyncProblem.DAMAGED_PAIRING -> R.string.ds_error_damaged
        SyncProblem.TOO_LARGE -> R.string.ds_error_too_large
        SyncProblem.INTERNAL -> R.string.ds_error_internal
    }
)

@Composable
private fun DesktopSyncIntro() {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.Computer, null, Modifier.size(40.dp))
            Text(stringResource(R.string.ds_welcome_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.ds_intro), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.ds_how_to_pair), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DesktopSyncHeader(name: String, busy: Boolean, age: SyncAge, changes: String?) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.Computer, null, Modifier.size(40.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // A problem is worded in its own card under this one; the age of the last good sync still holds.
                    Text(if (busy) stringResource(R.string.ds_syncing) else ageText(age), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (changes != null) Text(stringResource(R.string.ds_last_changes, changes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DesktopSyncNotice(problem: SyncProblem, onRepair: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Info, null)
                Text(problemText(problem), style = MaterialTheme.typography.bodyMedium)
            }
            if (onRepair != null) {
                TextButton(
                    onClick = onRepair, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) { Text(stringResource(R.string.ds_repair)) }
            }
        }
    }
}

@Composable
private fun SearchingCard() {
    SettingsCard {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LoadingIndicator(Modifier.size(32.dp))
            Text(stringResource(R.string.ds_searching), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NothingFoundCard() {
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ds_nothing_found_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ds_nothing_found_help, DEFAULT_SYNC_PORT), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FoundDesktopRow(desktop: FoundDesktop, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Computer, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(desktop.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${desktop.host}:${desktop.port}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}

/**
 * The code entry, with the address above it for the manual case ([address] null
 * for a desktop picked from the list). The field shows the code as the client
 * will send it: upper case, no dash, no space, 8 characters at most.
 */
@Composable
private fun PairCodeCard(
    title: String,
    address: String?, onAddress: (String) -> Unit,
    code: String, onCode: (String) -> Unit,
    busy: Boolean, canPair: Boolean, onPair: () -> Unit
) {
    SettingsCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (address != null) {
                OutlinedTextField(
                    value = address, onValueChange = onAddress, singleLine = true, enabled = !busy,
                    label = { Text(stringResource(R.string.ds_manual_label)) },
                    supportingText = { Text(stringResource(R.string.ds_manual_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next,
                        autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
                )
            }
            OutlinedTextField(
                value = code, onValueChange = onCode, singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.ds_code_label)) },
                supportingText = { Text(stringResource(R.string.ds_code_hint)) },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { if (canPair && !busy) onPair() }),
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
            )
            Button(
                onClick = onPair, enabled = canPair && !busy, shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (busy) LoadingIndicator(Modifier.size(24.dp)) else Text(stringResource(R.string.ds_pair))
            }
        }
    }
}
