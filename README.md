# Triat for Android

Triat is a music player for YouTube. This is the Android app. There is also a desktop app, made by the same person, at [github.com/kerstz/triat](https://github.com/kerstz/triat).

You can search for songs and videos, play them, build playlists, like tracks and download them. It works without a YouTube API key, and signing in is optional.

## Based on Koda

This app is a fork of [Koda](https://github.com/Ivorisnoob/Koda) by Ivorisnoob. Almost all of the player, the interface and the YouTube code come from Koda. Thank you to Ivorisnoob and to everyone who worked on it.

What Triat changes so far:

- New name, new icon, and a dark "Nothing" style palette by default.
- Import a plain text list of songs into a playlist.
- The Kotlin package is still called `com.ivor.ivormusic`, on purpose, to keep it easy to follow Koda.

## Vibe coded, please read this

A large part of this code was written by an AI. I did not read every line. I test the app by hand on my own phone, and I fix what breaks. If you want to use Triat, expect rough edges and things that may break after a YouTube change. Bug reports are welcome.

## What is coming

Wi-Fi sync with the Triat desktop app. Your playlists, likes and listening history will be kept the same on the phone and on the computer, over your home network, with no cloud in between. The phone pairs with the desktop app using a short code shown on the computer, and everything is sent encrypted. This is still being built.

## Build it yourself

You need a recent Android Studio and a device or emulator running Android 11 or newer.

```bash
git clone <this repository>
cd <the folder>
./gradlew assembleDebug
```

The `docs/` folder and [CLAUDE.md](CLAUDE.md) explain how the code is organised.

## License

Triat is free software under the [GNU General Public License v3.0](LICENSE), the same license as Koda. The full text is in the `LICENSE` file. If you share a modified version, it must stay under the same license and its source must be available.

Koda is Copyright 2026 Ivorisnoob. Triat additions are by kerstz.
