# Triat for Android

A music player for YouTube. No ads, no account required, no API key.

Search for a song, see its cover, play it.

<p align="center">
  <img src="docs/screenshots/search-symbiose-dona.png" width="260" alt="Search results for symbiose dona, each with its cover">
  <img src="docs/screenshots/search-vialice-two-drugs.png" width="260" alt="Search results for vialice two drugs, each with its cover">
  <img src="docs/screenshots/player.png" width="260" alt="The player">
</p>

Every search result comes with its artwork, so you can tell the original from
the slowed edit or the one-hour loop before you tap.

## Features

- Search songs, artists, albums and playlists, on YouTube Music or SoundCloud
- Background playback with a queue, shuffle and repeat
- SponsorBlock: skips intros, talking and sponsor reads
- Discovery through the artists you already listen to, with the source of each
  suggestion shown
- Playlists, likes, listening history and offline downloads
- Import a plain text list of songs into a playlist
- Wi-Fi sync with [Triat on desktop](https://github.com/kerstz/triat), no cloud
- Dark, quiet interface, in French and English

## Install

Download the APK from the [Releases](https://github.com/kerstz/triat-android/releases)
page and open it on your phone. On a recent phone, take the `arm64-v8a` one.
Android 11 or newer is required.

## Sync with the desktop app

Playlists, likes and history stay the same on the phone and on the computer.
Pair once, with the short code or by scanning the QR code shown in the desktop
app (Settings, Synchronisation, Pair a phone). After that the phone syncs by
itself whenever it is on the same Wi-Fi. Everything is encrypted, and your
YouTube session is only shared if you allow it on both sides.

## Based on Koda

Triat is a fork of [Koda](https://github.com/Ivorisnoob/Koda) by Ivorisnoob.
The player, most of the interface and the YouTube code come from there. Thank
you to Ivorisnoob and to everyone who worked on it.

What changes here:

- New name, new icon, a quieter dark palette
- The whole app translated to French
- SoundCloud as a second source
- Discovery through an artist graph instead of keyword searches
- SponsorBlock tuned for music
- Wi-Fi sync with the desktop app, with QR pairing
- Less on screen: fewer buttons, fewer shelves, fewer settings

The Kotlin package is still called `com.ivor.ivormusic`, on purpose, so Koda's
changes stay easy to follow.

## Heads up

Most of this code was written with an AI, and I did not read every line. I use
the app daily on my own phone and fix what breaks. Expect rough edges, and
expect things to break when YouTube changes something. Bug reports are welcome.

## Build it

You need a recent Android Studio and a device or emulator on Android 11 or newer.

```bash
git clone https://github.com/kerstz/triat-android.git
cd triat-android
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. For a signed release
build, see [docs/build-apk.md](docs/build-apk.md). The `docs/` folder and
[CLAUDE.md](CLAUDE.md) explain how the code is organised.

## License

Free software under the [GNU General Public License v3.0](LICENSE), the same
as Koda. If you share a modified version, it must stay under the same license
and its source must be available.

Koda is Copyright 2026 Ivorisnoob. Triat additions are by kerstz.
