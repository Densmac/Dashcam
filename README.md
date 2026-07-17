# Dashcam Companion

A clean, fast Android companion app for Novatek-based Wi-Fi dashcams — a privacy-respecting
replacement for the stock vendor app. Watch the live feed, browse and play your recordings without
downloading them, save clips to your phone, and manage the camera, all from a minimalist interface.

> **Unofficial & independent.** This project is not affiliated with, endorsed by, or sponsored by any
> dashcam manufacturer. It talks to the camera over its documented local HTTP/RTSP interface only.

## Features

- **Live preview** — low-latency RTSP view of the front or rear camera, with in-place camera
  switching, snapshot, and start/stop recording.
- **In-app playback** — stream recordings straight from the camera over Wi-Fi without downloading
  first. Playback is smooth and fully seekable (scrub, fast-forward, rewind) with a real duration,
  even though the camera serves raw MPEG-TS.
- **Smart buffering** — recordings are progressively cached to the phone while playing, so playback
  never stutters on the camera's bursty single-session delivery, and already-buffered positions seek
  instantly.
- **Transfers** — download front/rear clips to your phone with robust, resumable transfers; play them
  offline in-app, open in your favourite external player, or share to any app (WhatsApp, etc.).
- **Gesture navigation** — swipe left/right to move between clips, tap-to-reveal auto-fading
  controls, portrait and fullscreen-landscape playback.
- **Fast thumbnails** — a persistent disk cache plus background prefetch keep the library grid quick,
  even far down the list, within the camera's single-connection limit.
- **Device management** — change the Wi-Fi SSID and password, format the SD card, set loop-recording
  duration, timelapse, microphone/OSD, and speaker settings.
- **Minimalist design** — Material 3, light/dark/system themes, tasteful typography, haptics.

## Compatibility

Built and verified against a Novatek-based dual-channel dashcam that exposes:

- HTTP control + file API at `http://192.168.169.1/`
- RTSP live stream at `rtsp://192.168.169.1:554`

Many Novatek/"Viidure"-style cameras share this interface, but behaviour varies by firmware. Your
mileage may vary on other models. Requires **Android 8.0 (API 26)** or newer.

**How to use:** connect your phone to the dashcam's Wi-Fi network, then open the app — it detects the
camera automatically.

## Download

Grab the latest signed APK from the [**Releases**](../../releases) page and install it (you may need
to allow installing apps from your browser/file manager). The app is not on the Play Store.

## Build from source

```bash
git clone https://github.com/Densmac/Dashcam.git
cd Dashcam
./gradlew :app:assembleDebug        # debug APK, no signing needed
```

To build a **signed release** APK, create a `keystore.properties` file in the repo root (it is
gitignored — never commit it):

```properties
storeFile=/absolute/path/to/your.jks
storePassword=********
keyAlias=your-key-alias
keyPassword=********
```

Then:

```bash
./gradlew :app:assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

The same four values can instead be supplied as `DASHCAM_STORE_FILE`, `DASHCAM_STORE_PASSWORD`,
`DASHCAM_KEY_ALIAS`, and `DASHCAM_KEY_PASSWORD` environment variables (handy for CI).

## Tech stack

Kotlin · Jetpack Compose (Material 3) · MVVM · Hilt · Coroutines/Flow · Room · DataStore ·
WorkManager · OkHttp/Retrofit/Gson · LibVLC (RTSP + file playback).

## License

Licensed under the **Apache License, Version 2.0** — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

LibVLC is bundled under its own **LGPL-2.1-or-later** license and remains governed by it; see NOTICE
for third-party attributions.
