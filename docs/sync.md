# Wi-Fi sync with Triat desktop (`sync/`)

Reference detail moved out of `CLAUDE.md` so it loads only when needed.

## What it does

The phone pairs once with the Triat desktop app by typing an 8-character code shown on the desktop. After that, over Wi-Fi or Ethernet, it synchronizes playlists, likes and listening history and, if both sides allow it, the YouTube session. The desktop is the server and does the merge; the phone sends a full snapshot and applies what comes back. Nothing leaves the local network, and every exchange is encrypted.

## 1. Protocol

The desktop side lives in the Triat desktop repository (`triat/core/sync.py`, `triat/core/sync_state.py`). On the phone, the crypto is in `sync/SyncCrypto.kt`, tested against vectors produced by the desktop (`SyncCryptoTest`). Never change the protocol on one side only.

### 1.1 Discovery
- mDNS / DNS-SD, service type **`_triat._tcp`**. TXT properties: `v` (version, "1"), `name` (for example "Triat - host-name"), `id` (the desktop's identifier, hex).
- Default port **47823** (it may differ if that one is taken: use the port from the announcement).
- A desktop announces every private IPv4 it has (Docker, VPN, link-local ones included). The phone keeps IPv4 only (the server binds IPv4) and prefers RFC1918 (10/8, 172.16/12, 192.168/16) over any other (`DesktopDiscovery.pickHost`).
- A TXT `v` other than `SyncWire.VERSION` is listed with that `version`; pairing it answers "incompatible" rather than hiding it.
- Fallback: manual entry of `ip:port`.

### 1.2 HTTP endpoints
Everything is plain HTTP on the local network.

| Method | Path | Body sent | Response |
|---|---|---|---|
| GET | `/v1/hello` | none | JSON `{"app":"triat","v":1,"server_id":..,"name":..,"pairing":bool,"now":<epoch ms>}` (`now` may be absent) |
| POST | `/v1/pair/start` | JSON `{"device_id":str,"name":str,"pub":b64}` | 200 `{"server_id","name","pub":b64}`; 409 if no code is displayed |
| POST | `/v1/pair/finish` | JSON `{"device_id":str,"confirm":b64}` | 200 `{"confirm":b64}`; 403 wrong code; 409 closed |
| POST | `/v1/sync` | sealed binary, header `X-Triat-Device: <device_id>` | 200 sealed binary; 401 unknown or tampered; 400 version; 500 server error |

- Errors carry a small JSON body (`{"error": ...}`), including 401 for an unknown device and 500 for a server fault: the socket is no longer dropped. The phone keys on the status only (401 -> pair again, 400 -> version, 500 -> server), never on the body, so a body it cannot read changes nothing.

- `b64` is standard Base64 (with `=`). `pub` is an uncompressed P-256 point (65 bytes, starting with 0x04), that is `SyncCrypto.encodePublic`.
- `device_id` is a random string, stable per phone (a UUID), at most 64 characters.

### 1.3 Pairing
The QR code is `triat://pair?host=A&host=B&port=P&code=C&id=S`. `host` may be repeated: the first is the desktop's default-route address, the others its other private LAN addresses. `parsePairingUri` keeps those that pass `isPrivateHost` (in order) and fails only when none does. `pairWith` asks each in turn (`/v1/hello`) and takes the first answer whose `server_id` equals `id`, then falls back to an mDNS result with the same id; a desktop that answers with another id is "unverified" (`SyncProblem.UNVERIFIED`), none at all is "not found".

1. The user taps "Pair a phone" on the desktop. The desktop displays an 8-character code (alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789`), valid for 120 s, 5 attempts.
2. Phone: `kp = SyncCrypto.newKeyPair()`, then POST `/v1/pair/start`.
3. Phone: `key = SyncCrypto.deriveKey(kp.private, unb64(desktop_pub), code)` and `tr = SyncCrypto.transcript(deviceId, phonePub, desktopPub)`, then POST `/v1/pair/finish` with `confirm = b64(SyncCrypto.confirmation(key, "client", tr))`.
4. On a 200 response, check that `unb64(confirm)` equals `confirmation(key, "server", tr)`. Otherwise it is a failure and nothing is saved.
5. Save (encrypted): `server_id`, `name`, `host`, `port`, `key`, `device_id`.

### 1.4 Sync
- The phone sends `SyncCrypto.seal(key, json, deviceId)` to `/v1/sync` and opens the response with `SyncCrypto.open(key, blob, deviceId)`.
- The JSON sent is the phone's **complete snapshot**. The JSON received is the **merged state** (merged by the desktop), to be applied as is on the phone.
- Format (timestamps in **milliseconds**, durations in floating-point **seconds**):

```json
{"v": 1,
 "playlists": [{"uid": "...", "name": "...", "updated": 1700000000000,
                "deleted": false, "tracks": [TRACK, ...]}],
 "likes":     [{"id": "<videoId>", "at": 1700000000000, "liked": true, "track": TRACK}],
 "history":   [{"id": "<videoId>", "at": 1700000000000, "seconds": 183.0, "track": TRACK}]}
TRACK = {"id": "<videoId, 11 chars>", "title": "...", "artist": "...",
         "duration": 183.0, "thumb": "https://..." or null}
```

- The response may also contain `"now"` (the desktop's epoch milliseconds). When it differs from the phone's clock by more than 120 s, the sync still applies but ends with `ERROR_CLOCK` (`clockProblem`, `SyncProblem.CLOCK`): last-writer-wins on skewed clocks picks the wrong side and the phone drops plays dated in its future.
- The response may also contain `"cookies": "<Cookie header>"` if the user enabled sharing on the desktop.
- A deleted playlist is `"deleted": true, "tracks": []`, with `updated` set to the deletion date. A removed like is `"liked": false, "track": null`.
- **Only YouTube tracks** (11-character ids) are synchronized. The phone's local songs (`SongSource.LOCAL`) are excluded.
- A deletion received from the desktop keeps the author's date: `unlikeSongAt(id, at)` records the tombstone with `at`, never now, so an edit made elsewhere in between still beats it (`ApplyPlan.likesToRemove` carries `(id, at)`).
- A desktop copy of a playlist never holds the phone's local songs; `applySynced` puts them back at their old index, clamped (`SyncWire.mergeLocalSongs`).
- Merge rules (applied by the desktop; the phone applies the result): the most recent playlist wins (deletion included), the most recent like wins, history is the union deduplicated to the second, capped at the 3000 most recent entries.

## Files

| File | Role |
|---|---|
| `SyncCrypto.kt` | ECDH P-256 + HKDF key, pairing proofs, sealing of every exchange; byte-compatible with the desktop |
| `SyncWire.kt` | Koda models to and from the wire JSON (`snapshot`), and what to apply from the merged state (`plan`) |
| `SyncTombstones.kt` | Deleted playlists and unliked songs, so the desktop does not hand them back |
| `SyncStore.kt` | The paired desktop, its key and the last sync state, in an encrypted preference file excluded from backups |
| `DesktopDiscovery.kt` | mDNS discovery of `_triat._tcp`, one resolution at a time |
| `DesktopSyncClient.kt` | HTTP client: `hello`, `pair`, `sync`; refuses any non-private host |
| `DesktopSyncRepository.kt` | Process-wide owner of the pairing, the discovery list and the one-at-a-time sync |
| `SyncRefresh.kt` | Tells every holder of its own `PlaylistRepository` that a sync wrote to disk |
| `AutoSyncPolicy.kt` | The pure decision of when an automatic sync may run |
| `AutoSync.kt` | Gathers the inputs of that decision and runs the sync; started once from `IvorMusicApplication` |
| `ui/settings/DesktopSyncSettings.kt` | The Settings page: find, pair, sync now, receive the session, forget |

## Automatic sync

The decision lives in `AutoSyncPolicy.kt`, apart from everything Android so it can be tested on the JVM. Time is an argument, never read there. `AutoSync` gathers the inputs and acts on the answer. The rules:

- Never without a pairing, never off the local network (Wi-Fi or Ethernet, never mobile data), and never while a sync is already running.
- Coming to the foreground syncs at most once every `FOREGROUND_MIN_GAP_MS` (5 minutes).
- A local edit syncs once nothing has changed for `CHANGE_DEBOUNCE_MS` (30 seconds).
- An edit that a sync itself made (it applies playlists and likes) is not an edit: `isSyncEcho` drops it, or every sync would trigger the next one.

## Traps already paid for

- `NsdManager` resolves one service at a time on older Android, so resolutions are queued.
- Cleartext HTTP is allowed app-wide (`network_security_config.xml`), so `DesktopSyncClient` refuses any host that is not private (`isPrivateHost`), and every payload is sealed by `SyncCrypto`.
- `unlikeSong` writes a tombstone on purpose: without it the desktop would hand the like back. One that comes from the desktop uses `unlikeSongAt` so the tombstone keeps the author's date; a tombstone dated now is sent back to every device and beats a newer like.
- `SyncTombstones.record`/`clear` read-modify-write one preference string from several instances: they share one process-wide lock.
- The desktop does the merge; the phone sends a full snapshot and applies what comes back. Never change the protocol on one side only.
- Only YouTube tracks (11-character ids) travel. Local songs never do.
- In incognito the history is not sent; playlists and likes still are.
- A screen holding its own `PlaylistRepository` must observe `finishedSyncs()` (`SyncRefresh.kt`), or it keeps showing, then writes back, the playlists from before the sync.
- The pairing key never enters a backup (`backup_rules.xml`, `data_extraction_rules.xml`, `BackupRepository`), while the tombstones do, or a restore would bring deleted items back.

## Tests

- `sync/AutoSyncPolicyTest.kt`: the automatic sync's decision, on a clock the test moves by hand.
- `sync/DesktopDiscoveryTest.kt`: TXT decoding and address choice (RFC1918 over other IPv4, no IPv6, no loopback), TXT version.
- `sync/DesktopSyncClientTest.kt`: the HTTP client against a simulated desktop with the real `SyncCrypto`, and the private-host guard.
- `sync/DesktopSyncRepositoryTest.kt`: the pure decisions of the repository, on invented data.
- `sync/SyncCryptoTest.kt`: interop vectors produced by the desktop; a failure means the two sides no longer understand each other.
- `sync/SyncRefreshTest.kt`: which state changes count as a finished sync (`finishedSyncs`).
- `sync/SyncStoreTest.kt`: the saved pairing, the device id, forgetting and damaged records.
- `sync/SyncWireTest.kt`: the wire format, the snapshot, the plan applied from a merged state and `mergeLocalSongs`, on invented data.
- `ui/settings/DesktopSyncSettingsTest.kt`: what the code field keeps, how a typed address is read, how old a sync is called, which message each failure gets.

## E1 manual check

The parts that need a device and a desktop on the network are checked by hand.

1. Install the release APK for arm64.
2. At first launch: the app is named Triat, is black with the red accent, and searching and playing a track work.
3. Settings > About: "Source code" opens the Triat Android repository; "Based on Koda" opens Koda's repository. Settings > Updates: "no published version", without an error.
4. Desktop: Settings > Synchronization > enable > "Pair a phone".
5. Phone: Settings > Computer (Triat) > the desktop appears > enter the displayed code.
6. Check: the desktop's playlists arrive on the phone; a playlist created on the phone reaches the desktop after "Sync now"; deleting a playlist on one side deletes it on the other; a like added or removed follows; the history is completed on both sides.
7. Turn the phone's Wi-Fi off: no sync, no blocking error, nothing over mobile data.
8. Restart the desktop app: the phone syncs again without pairing again.
9. If the desktop is not found: `triat doctor` must say why (port, firewall); also try manual entry `address:port`.
10. Download a track: it appears in Files > Download > Triat > Music and plays offline.
