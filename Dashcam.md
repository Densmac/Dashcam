# Dashcam Implementation Audit And Handoff Spec

Last updated: 2026-07-14

This file describes the Android app as implemented in this repository. It is
also a handoff note for the next agent. Treat this as current implementation
state, not an aspirational product brief.

## Current Status Summary

Implemented app:

```text
App name: Dashcam
Namespace: com.densmac.dashcam
Application ID: com.densmac.dashcam
Version code: 1
Version name: 1.0
Min SDK: 26
Compile SDK: 37
Target SDK: 37
UI: Jetpack Compose
Architecture: MVVM, repositories, use cases
DI: Hilt
Database: Room
Preferences: DataStore
Background work: WorkManager
HTTP: OkHttp + Retrofit + Gson
Live preview: LibVLC, RTSP over TCP, video-only
```

Latest known build state:

```text
Last successful full build/test before the final live-tab resume patch:
./gradlew --no-configuration-cache :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug

Result: BUILD SUCCESSFUL

Important: after that successful build, additional source changes were made to:
- LiveScreen.kt
- LiveViewModel.kt
- RtspLivePreviewController.kt
- AppNavGraph.kt

Those latest changes must be rebuilt and installed before runtime testing.
```

Device context from the active debugging session:

```text
Physical phone ADB serial: 3344bd2b
Dashcam host: 192.168.169.1
Phone route observed:
192.168.169.0/24 dev wlan0 src 192.168.169.2
The user may be using the phone hotspot/mobile data while also connected to dashcam Wi-Fi.
Tell the user before requiring a Wi-Fi switch.
```

Installed-app instrumentation context:

```text
The user explicitly allowed autonomous use of the installed apps via ADB:
- Dashcam, this app
- Viidure, the vendor app that came with the dashcam
- PCAPdroid, for fresh protocol captures

Known package names:
- Dashcam: com.densmac.dashcam
- Viidure: com.vidure.app, observed in Android logs
- PCAPdroid: com.emanuelef.remote_capture
```

ADB launch helpers:

```text
Launch Dashcam:
adb -s 3344bd2b shell monkey -p com.densmac.dashcam -c android.intent.category.LAUNCHER 1

Launch Viidure:
adb -s 3344bd2b shell monkey -p com.vidure.app -c android.intent.category.LAUNCHER 1

Launch PCAPdroid after package discovery:
adb -s 3344bd2b shell monkey -p com.emanuelef.remote_capture -c android.intent.category.LAUNCHER 1
```

ADB test helpers:

```text
Clear logs:
adb -s 3344bd2b logcat -c

Read relevant logs:
adb -s 3344bd2b logcat -d -v time | grep -E 'Dashcam|OkHttp|LibVLC|VLC|DownloadWorker|WorkManager|WM-|setparamvalue|getrecduration|getmediainfo|getthumbnail|getfilelist|playback|setting|enterrecorder|mnt/sdcard'

Capture screenshot:
adb -s 3344bd2b exec-out screencap -p > /tmp/dashcam_screen.png

Dump UI hierarchy:
adb -s 3344bd2b shell uiautomator dump /sdcard/window.xml
adb -s 3344bd2b shell cat /sdcard/window.xml
```

How to check whether the phone is on dashcam Wi-Fi:

```text
Check route to dashcam subnet:
adb -s 3344bd2b shell ip route | grep 192.168.169

Expected route when connected:
192.168.169.0/24 dev wlan0 src 192.168.169.2

Observed non-dashcam route during handoff package-name check:
192.168.100.0/24 dev wlan0 src 192.168.100.25

If wlan0 is 192.168.100.* instead of 192.168.169.*, ADB is connected but the phone is not on DASHCAM Wi-Fi.

Check dashcam reachability:
adb -s 3344bd2b shell ping -c 1 -W 1 192.168.169.1

Check Wi-Fi details if available on the device build:
adb -s 3344bd2b shell dumpsys wifi | grep -iE 'DASHCAM|SSID|WifiInfo|Supplicant'

Check connectivity view:
adb -s 3344bd2b shell dumpsys connectivity | grep -iE 'wlan0|192.168.169|WIFI|NetworkAgent'
```

Operational note:

```text
If dashcam Wi-Fi is not connected, ask/tell the user before requiring a switch.
The user may lose normal internet during dashcam Wi-Fi tests because the dashcam network is local-only.
If a fresh protocol capture is needed, use PCAPdroid with Viidure first, then repeat with Dashcam for comparison.
```

## Privacy And Permissions

Implemented:

```text
No Firebase
No backend server
No login
No analytics SDK
No crash reporting SDK
No ads
No remote config
No cloud sync
allowBackup=false
```

Declared app permissions:

```text
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
CHANGE_NETWORK_STATE
FOREGROUND_SERVICE
FOREGROUND_SERVICE_DATA_SYNC
POST_NOTIFICATIONS
```

Downloads are app-specific files under external app storage. The app does not
request broad shared-storage permissions.

## Navigation And UI

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/navigation/AppNavGraph.kt
app/src/main/java/com/densmac/dashcam/ui/navigation/Routes.kt
app/src/main/java/com/densmac/dashcam/ui/screens/live/LiveScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/library/LibraryScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/downloads/DownloadsScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/settings/SettingsScreen.kt
```

Implemented navigation:

```text
MainTabs route
Live tab
Library tab
Downloads tab
Settings route
Clip detail route
```

Main UI shell:

```text
Three horizontally swipeable bottom tabs: Live, Library, Downloads
Settings icon top right
Round app mark top left
Centered header title
Centered page titles
Icon-forward controls with minimal text
Warm amber/clay/cream palette with black-olive surfaces
System light/dark theme aware
Optional dynamic color setting
Haptics on app buttons and tab controls
```

Bottom tabs are functional navigation controls, not cosmetic.

## Theme

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/core/design/theme/Theme.kt
app/src/main/java/com/densmac/dashcam/core/design/theme/Color.kt
app/src/main/java/com/densmac/dashcam/data/datastore/UserPreferences.kt
app/src/main/java/com/densmac/dashcam/data/datastore/UserPreferencesDataSource.kt
```

Implemented:

```text
ThemeMode.SYSTEM follows Android system theme.
ThemeMode.LIGHT forces light.
ThemeMode.DARK forces dark.
Dynamic color is available on Android S+ if enabled.
Default should be SYSTEM unless the user saved another preference.
```

## Network Binding And Monitoring

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/core/network/DashcamNetworkBinderImpl.kt
app/src/main/java/com/densmac/dashcam/core/network/DashcamConnectionMonitorImpl.kt
app/src/main/java/com/densmac/dashcam/core/network/NetworkModule.kt
```

Implemented binding behavior:

```text
Enumerates Android networks.
Filters Wi-Fi networks.
Prefers networks with 192.168.169.* link address or gateway 192.168.169.1.
Verifies http://192.168.169.1/app/getdeviceattr contains result 0.
Binds process with ConnectivityManager.bindProcessToNetwork(network).
Unbinds when verification fails.
```

Implemented monitor behavior:

```text
Reference-counted singleton monitor.
Poll interval: 2500 ms.
Registers a Wi-Fi NetworkCallback so an already-running app can react when the phone connects to dashcam Wi-Fi.
Does not emit repeated Searching states every poll.
Keeps last Connected through one transient failure to reduce UI flicker.
Emits unreachable after two consecutive failures.
```

Issue addressed:

```text
Dashcam Wi-Fi card flicker was reduced by holding Connected through one transient probe failure.
App should detect dashcam Wi-Fi after system connects without restarting the app.
This should still be re-tested on device.
```

## Confirmed Dashcam Protocol

Do not guess alternate protocols unless new packet captures prove otherwise.

Confirmed live protocol:

```text
RTSP over TCP
Primary app URL: rtsp://192.168.169.1:554
The camera exposes track1/track2 under the RTSP root.
Video: H.264
Preview size observed by OS media logs: 800x480
Audio: malformed AAC stream; app disables audio.
```

Important:

```text
Do not use port 5000 for live preview.
Do not use MJPEG, WebSocket, HLS, or ExoPlayer HTTP progressive playback.
Media3 RTSP failed on the malformed stream; LibVLC is the current MVP player.
```

Confirmed HTTP endpoints in use:

```text
GET /app/getdeviceattr
GET /app/getproductinfo
GET /app/getmediainfo
GET /app/playback?param=enter
GET /app/playback?param=exit
GET /app/setting?param=enter
GET /app/setting?param=exit
GET /app/enterrecorder
GET /app/getparamitems?param=all
GET /app/getparamvalue?param=all
GET /app/setparamvalue?param=<name>&value=<int>
GET /app/getsdinfo
GET /app/getrecduration
GET /app/getbatteryinfo
GET /app/snapshot
GET /app/getfilelist?folder=<folder>&start=<start>&end=<end>
GET /app/getthumbnail?file=/mnt/sdcard/...
GET /app/deletefile?file=/mnt/sdcard/...
GET /mnt/sdcard/... for raw file downloads
```

Device management protocol (reverse-engineered from Viidure via PCAPdroid, 2026-07-15):

```text
These cover the previously-deferred features (Wi-Fi SSID/password, Format SD).
All are wrapped by Viidure in: rec-stop + settings-enter, matching how the app already
wraps setparamvalue in setting enter/exit.

Change Wi-Fi SSID:
  GET /app/setparamvalue?param=rec&value=0     (stop recording)
  GET /app/setting?param=enter
  GET /app/setwifi?wifissid=<newName>          -> {"result":0,"info":"set success"}
  GET /app/wifireboot                          (restarts AP; returns {result: 98} but reboots)
  GET /app/exitrecorder
  (phone must then reconnect to the new SSID)

Change Wi-Fi password:
  GET /app/setparamvalue?param=rec&value=0
  GET /app/setting?param=enter
  GET /app/setwifi?wifipwd=<newPassword>       -> {"result":0,"info":"set success"}
  GET /app/wifireboot
  GET /app/exitrecorder
  (phone must then reconnect with the new password)

Format SD card (DESTRUCTIVE - wipes all footage):
  GET /app/setparamvalue?param=rec&value=0
  GET /app/setting?param=enter
  GET /app/sdformat?index=1                    (index=1 = the SD slot)

Param names verified exact: wifissid, wifipwd (wifipasswd/wifipassword are rejected).
Value constraints (Viidure UI): SSID 4-22 chars, password 8-22 chars, alphanumeric only,
no special characters.

Other endpoints discovered this session (not yet used by the app):
  GET /app/exitrecorder      (opposite of enterrecorder)
  GET /app/getstorageinfo
  GET /app/settimezone?timezone=<int>
  GET /app/setsystime?date=YYYYMMDDHHMMSS
```

Critical encoding detail:

```text
The dashcam thumbnail/delete file query must preserve raw slash paths.
Correct:   /app/getthumbnail?file=/mnt/sdcard/VIDEO_F/example.ts
Wrong:     /app/getthumbnail?file=%2Fmnt%2Fsdcard%2FVIDEO_F%2Fexample.ts

Retrofit annotations currently use @Query("file", encoded = true).
DashcamApiMockWebServerTest has a regression test for this.
```

## Live Preview Implementation

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/screens/live/LiveScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/live/LiveViewModel.kt
app/src/main/java/com/densmac/dashcam/domain/usecase/PrepareLivePreviewUseCase.kt
app/src/main/java/com/densmac/dashcam/domain/usecase/SwitchCameraUseCase.kt
app/src/main/java/com/densmac/dashcam/core/player/RtspLivePreviewController.kt
app/src/main/java/com/densmac/dashcam/core/player/LivePreviewEngine.kt
```

Current player:

```text
LibVLC via org.videolan.android:libvlc-all
VLCVideoLayout hosted inside AndroidView/FrameLayout
RTSP root URL: rtsp://192.168.169.1:554
Options:
--no-audio
--rtsp-tcp
--network-caching=150
--live-caching=150
--clock-jitter=0
--clock-synchro=0
Per-media:
:rtsp-tcp
:network-caching=150
:live-caching=150
:clock-jitter=0
:clock-synchro=0
:no-audio
```

Normal live start sequence:

```text
Bind process to dashcam Wi-Fi.
GET /app/playback?param=exit
GET /app/setting?param=exit
GET /app/enterrecorder
GET /app/getmediainfo
Apply preferred camera through SwitchCameraUseCase.
Start LibVLC RTSP at rtsp://192.168.169.1:554.
```

Camera switch sequence currently implemented:

```text
livePreviewEngine.stop()
delay 250 ms
GET /app/setparamvalue?param=switchcam&value=<0 or 1>
Persist preferredCamera after successful switch command.
delay 120 ms
GET /app/getrecduration
GET /app/getmediainfo
Start LibVLC RTSP at rtsp://192.168.169.1:554
If RTSP start fails once:
  delay 500 ms
  GET /app/playback?param=exit
  GET /app/setting?param=exit
  GET /app/enterrecorder
  GET /app/getrecduration
  GET /app/getmediainfo
  Start RTSP once more
```

Camera values:

```text
Default: FRONT -> 0, REAR -> 1
Swapped setting: FRONT -> 1, REAR -> 0
Same RTSP URL is used for both; switchcam changes active feed.
```

Latest live changes not yet verified after build:

```text
LiveViewModel serializes all preview commands with previewCommandMutex.
Camera rail ignores active/redundant camera taps while busy.
LiveScreen now receives isVisible from MainTabs.
When Live tab becomes visible, LiveViewModel.resumePreview() starts the last selected feed if connected and not already active.
When leaving Live, LiveScreen calls engine.release() so Library/Downloads do not fight an open RTSP stream.
RtspLivePreviewController now releases the LibVLC instance before each new start to avoid stale RTSP state after repeated camera switches.
```

Live issue RESOLVED 2026-07-15 (verified on device):

```text
Root cause of "repeated front/rear switching kills the feed":
The old switch flow stopped RTSP, sent switchcam, then reopened a NEW RTSP session.
The dashcam serves a SINGLE RTSP session and swaps the active camera into that same
stream in place. Reopening the session on every switch makes the dashcam stall the new
PLAY (VLC connects, port 554 answers OPTIONS 200, but no frames arrive -> 5s timeout).
The feed died on the very FIRST switch, and rapid switching killed it entirely.

Fix in SwitchCameraUseCase: when the engine is already Playing, send ONLY switchcam and
keep the existing session; the feed swaps front<->rear in place. The full preflight
(playback exit, setting exit, enterrecorder, getmediainfo) + RTSP start now runs ONLY for
the initial start (nothing playing yet). Verified: 8 rapid F/R switches with zero RTSP
restarts, zero timeouts, feed never died.

NOTE: this contradicts the written spec section 8 (which says stop+reopen /track2 on
switch). The empirical on-device behavior wins. Do not reintroduce stop/reopen on switch.

Also RESOLVED: Live-tab resume after visiting Library. DashcamNetworkBinderImpl now binds on
the load-independent link signal (phone 192.168.169.x address / gateway) instead of a fresh
HTTP getdeviceattr verify. The verify competed with in-flight Library thumbnail requests and
timed out (NotConnectedToDashcam), which had broken resume. HTTP verify kept only as fallback.
```

Suggested switch-loop verification:

```text
./gradlew --no-configuration-cache :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug
adb -s 3344bd2b install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 3344bd2b logcat -c
adb -s 3344bd2b shell am force-stop com.densmac.dashcam
adb -s 3344bd2b shell monkey -p com.densmac.dashcam -c android.intent.category.LAUNCHER 1
```

Then tap camera rail repeatedly and inspect:

```text
adb -s 3344bd2b logcat -d -v time | grep -E 'Dashcam|OkHttp|LibVLC|VLC|setparamvalue|getrecduration|getmediainfo|playback|setting|enterrecorder|RTSP|live preview' | tail -n 300
```

Expected healthy pattern:

```text
Only one switch sequence at a time.
Direct switchcam call.
getrecduration + getmediainfo.
Starting LibVLC RTSP preview: rtsp://192.168.169.1:554
LibVLC live preview is playing
No overlapping timeout/retry loops.
```

## Library And Thumbnails

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/screens/library/LibraryScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/library/LibraryViewModel.kt
app/src/main/java/com/densmac/dashcam/data/repository/FileRepositoryImpl.kt
app/src/main/java/com/densmac/dashcam/domain/usecase/GetFileBundlesUseCase.kt
app/src/main/java/com/densmac/dashcam/domain/repository/FileRepository.kt
```

Implemented library behavior:

```text
Folder tabs: Loop, Event, Park, SOS, Race.
Calls /app/playback?param=enter before listing.
Calls /app/getfilelist?folder=<folder>&start=<start>&end=<end>.
Flattens nested info[].files[] response.
Pairs front/rear files by timestamp within 3 seconds.
Displays paired cards as split front/rear thumbnails.
Per-card icons: download and delete.
Long press enables selection.
Delete requires confirmation.
```

Folder mapping:

```text
Loop -> folder=loop, range 0..199
Event -> folder=event, range 0..99
Park -> folder=park, range 0..99
SOS -> folder=emr, range 0..99
Race -> folder=race, range 0..99
```

Thumbnail implementation:

```text
FileRepositoryImpl.getThumbnailBytes(path) calls /app/getthumbnail?file=/mnt/sdcard/...
Query path now preserves raw slashes.
LibraryViewModel owns thumbnail cache by file path.
In-flight thumbnail requests are kept in the ViewModel, not only in a LazyGrid item.
Good thumbnails persist while scrolling.
Tiny fallback responses are ignored if bytes < 4096.
Cache limit: 160 thumbnails.
```

On-device verification from 2026-07-14:

```text
Encoded path bug before fix:
GET /app/getthumbnail?file=%2Fmnt%2F... returned 1062-byte generic/fuzzy fallback.

Raw path after fix:
GET /app/getthumbnail?file=/mnt/sdcard/... returned real 15 KB to 30 KB thumbnails for older clips.
Some current/active clips still return 1062-byte generic placeholders from the dashcam itself.
The cache now ignores those tiny placeholders if no useful image exists or keeps the old good image if present.
```

Remaining thumbnail caveat:

```text
Newest/current recordings can still have missing thumbnails until the dashcam finishes writing/serving them.
That is camera behavior, not Compose scaling.
The UI now avoids overwriting real cached thumbnails with tiny placeholders.
```

## Downloads

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/data/repository/DownloadRepositoryImpl.kt
app/src/main/java/com/densmac/dashcam/data/download/DashcamDownloadManager.kt
app/src/main/java/com/densmac/dashcam/data/download/DownloadWorker.kt
app/src/main/java/com/densmac/dashcam/ui/screens/downloads/DownloadsScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/downloads/DownloadsViewModel.kt
```

Implemented download enqueue behavior:

```text
Stable download ID = first 24 hex chars of SHA-256(remotePath).
Completed row is accepted only if local file exists and length > 0.
Otherwise the DB row is upserted as QUEUED.
WorkManager unique work is enqueued with ExistingWorkPolicy.REPLACE.
Bundle download enqueues front and rear separately.
```

Implemented DownloadWorker behavior:

```text
Runs on Dispatchers.IO.
Sets foreground notification.
Binds to verified dashcam network.
GET /app/playback?param=enter before raw file GET.
Downloads from http://192.168.169.1/<remotePath without leading slash>
Example: http://192.168.169.1/mnt/sdcard/VIDEO_F/example.ts
Supports resume with Range when a partial file exists.
Writes <localPath>.partial then renames to final local path.
Updates Room progress.
```

Debug logs were added:

```text
Queue file download: ...
Replace download work: ...
DownloadWorker starting: ...
DownloadWorker bound to dashcam network
DownloadWorker requesting: ...
DownloadWorker response: code=... contentLength=...
DownloadWorker completed: ...
DownloadWorker failed: ...
```

Download issue RESOLVED 2026-07-15 (verified on device):

```text
Root cause of "downloads do not transfer":
WorkManager fell back to its default reflection WorkerFactory and could not construct the
@HiltWorker DownloadWorker (NoSuchMethodException on the (Context, WorkerParameters)
constructor). Enqueue logged fine ("Queue file download") but every worker failed at
"WM-WorkerFactory: Could not create Worker".

DashcamApp already provides Configuration.Provider + HiltWorkerFactory, but the default
androidx.startup WorkManagerInitializer was still enabled in AndroidManifest.xml, so it
initialized WorkManager with the default factory before the Hilt config was used.

Fix: added a <provider androidx.startup.InitializationProvider tools:node="merge"> block with
<meta-data androidx.work.WorkManagerInitializer tools:node="remove" /> to the manifest.
Verified: tap -> worker created via Hilt -> bind -> GET raw /mnt/sdcard/... -> HTTP 200 ->
write .partial -> rename to final .ts. A 136 MB rear file completed with exact byte count.
Guarded by WorkManagerConfigInstrumentedTest.
```

Suggested download verification:

```text
adb -s 3344bd2b logcat -c
Use uiautomator or screenshot to tap a visible Library card Download icon.
adb -s 3344bd2b logcat -d -v time | grep -E 'Queue file|Replace download|DownloadWorker|WorkManager|WM-|requesting|response|completed|failed|mnt/sdcard' | tail -n 300
adb -s 3344bd2b shell find /sdcard/Android/data/com.densmac.dashcam/files -maxdepth 6 -type f -print
```

If no `Queue file download` log appears:

```text
The UI action is not reaching LibraryViewModel.downloadBundle.
Inspect LibraryScreen TileAction and parent combinedClickable interaction.
Use uiautomator bounds to tap the clickable parent of the Download node, not only the icon glyph.
```

If `Queue file download` appears but no worker starts:

```text
Inspect Hilt WorkerFactory / WorkManager state.
DashcamApp implements Configuration.Provider with HiltWorkerFactory.
Check logcat WM-* lines.
```

If worker starts but raw GET fails:

```text
Compare with PCAP: vendor app downloads direct /mnt/sdcard/... paths.
Try curl from the connected machine/phone context if available:
curl http://192.168.169.1/mnt/sdcard/VIDEO_F/<file>.ts
```

## Settings

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/screens/settings/SettingsScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/settings/SettingsViewModel.kt
app/src/main/java/com/densmac/dashcam/data/repository/SettingsRepositoryImpl.kt
```

Implemented:

```text
Theme mode: System, Light, Dark
Dynamic color toggle
Haptics toggle
Auto-start live preview toggle
Preferred camera
Swap front/rear labels
Dashcam settings fetched from getparamitems/getparamvalue
Setting writes use setparamvalue
```

Important switchcam special case:

```text
SettingsRepositoryImpl handles param == "switchcam" as a direct setparamvalue call.
It does not wrap switchcam in setting enter/exit.
This matches observed Viidure behavior.
```

## Haptics

Relevant file:

```text
app/src/main/java/com/densmac/dashcam/core/design/haptics/HapticFeedbackManager.kt
```

Implemented:

```text
LocalDashcamHapticsEnabled composition local.
rememberHapticClick wrapper.
Modifier.hapticClickable.
DashcamButton uses haptic click.
Bottom tabs use haptic click.
Live controls use haptic click.
Library folder/card action controls use haptic click.
```

If adding new buttons, use `rememberHapticClick` or `Modifier.hapticClickable`.

## Tests

Relevant unit tests:

```text
app/src/test/java/com/densmac/dashcam/data/api/DashcamApiMockWebServerTest.kt
app/src/test/java/com/densmac/dashcam/data/api/DashcamApiMapperTest.kt
app/src/test/java/com/densmac/dashcam/domain/usecase/GetFileBundlesUseCaseTest.kt
app/src/test/java/com/densmac/dashcam/domain/usecase/DashcamFilenameParserTest.kt
app/src/test/java/com/densmac/dashcam/data/repository/SettingsRepositoryImplTest.kt
```

Instrumented tests (androidTest, run on device against the real dashcam) added 2026-07-15:

```text
app/src/androidTest/java/com/densmac/dashcam/WorkManagerConfigInstrumentedTest.kt
  - Asserts DashcamApp provides HiltWorkerFactory (guards the download regression).
app/src/androidTest/java/com/densmac/dashcam/DashcamProtocolInstrumentedTest.kt
  - Real-device protocol checks: getdeviceattr result 0, getsdinfo present,
    enterrecorder + getmediainfo, RTSP OPTIONS on port 554 returns 200, and a raw-slash
    getthumbnail returns real (>4096 byte) image bytes. Skips gracefully (JUnit Assume)
    when the dashcam is not reachable so the suite stays green without hardware.

Run them (AGP connectedAndroidTest may not detect the device; run directly instead):
  ./gradlew --no-configuration-cache :app:assembleDebugAndroidTest
  adb -s 3344bd2b install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb -s 3344bd2b shell am instrument -w \
    com.densmac.dashcam.test/androidx.test.runner.AndroidJUnitRunner
Result 2026-07-15: OK (7 tests).
```

Current notable coverage:

```text
Dashcam API success/error mapping.
Nested getfilelist response parsing.
Raw file query path regression for getthumbnail:
  /app/getthumbnail?file=/mnt/sdcard/VIDEO_F/...
Delete raw file query path:
  /app/deletefile?file=/mnt/sdcard/test.jpg
Front/rear file bundling behavior.
```

## PCAP And Vendor App Findings

Artifacts provided by user:

```text
/home/densmac/Downloads/PCAPdroid_13_Jul_17_31_32.pcap
/home/densmac/Downloads/PCAPdroid_13_Jul_19_24_40.pcap
/home/densmac/Downloads/PCAPdroid_13_Jul_19_24_40.pcap (1)
/home/densmac/dashcam_api_dump.zip
/home/densmac/dashcam_clean_probe_20260713_192223.zip
```

Vendor app name:

```text
Viidure
```

Observed Viidure behavior:

```text
Live preview uses RTSP root rtsp://192.168.169.1:554.
Camera switch uses direct /app/setparamvalue?param=switchcam&value=0 or 1.
After switch, Viidure calls /app/getrecduration and /app/getmediainfo before RTSP.
Thumbnails use raw /app/getthumbnail?file=/mnt/sdcard/...
File downloads use direct GET /mnt/sdcard/...
```

## Current Dirty Worktree Notes

The worktree is intentionally dirty with ongoing implementation. Do not revert
unrelated files unless the user explicitly asks.

Known recent source changes include:

```text
LibVLC replacing Media3 RTSP.
Raw thumbnail/delete file query paths.
Library split F/R thumbnails.
ViewModel thumbnail cache.
Connection monitor anti-flicker and network callback.
Download worker logging and playback enter preflight.
Live command mutex.
Live-tab resume hook.
Fresh LibVLC session per RTSP start.
Direct switchcam flow with one recovery preflight.
```

Before committing, run:

```text
git status --short
./gradlew --no-configuration-cache :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug
```

## Verification Status 2026-07-15

```text
Live-tab resume: FIXED and verified (link-based network binding).
Repeated camera switching: FIXED and verified (in-place switch, single RTSP session).
Downloads: FIXED and verified (WorkManager Hilt factory; manifest initializer removed).
Thumbnail persistence: verified good; scroll round-trip keeps real thumbnails.
Unit tests + 7 instrumented tests: passing on device.

NEXT MAJOR SCOPE (user brief 2026-07-15): in-app media streaming + downloaded-file
playback (MediaViewerScreen streaming remote .ts over HTTP via LibVLC), Transfers UX with
send-to-transfers animation, settings redesign, theme switch replacing the "Dashcam" header,
and a broader minimalist Material 3 Expressive revamp. Priority per user: make live preview
and in-app file streaming reliable/smooth FIRST, then the visual revamp. Do NOT reintroduce
stop/reopen-on-switch for the camera (see Live section) despite spec section 8.
```

## In-App Media Streaming (Phase 1 of the revamp) — DONE 2026-07-15

```text
New files:
- core/player/MediaPlaybackState.kt  (Idle/Opening/Buffering/Playing/Paused/Ended/Error)
- core/player/MediaFilePlayerController.kt  (LibVLC, separate instance from the live RTSP
  engine, lifecycle-safe; awaits the AndroidView surface via a CompletableDeferred so local
  playback does not race ahead of surface attach and strand on the spinner)

ClipDetailScreen is now the in-app media viewer:
- Tap Play -> streams the remote .ts over HTTP (http://192.168.169.1/mnt/sdcard/...), no
  download. Preflight = DashcamRepository.enterPlayback() (bind network + playback?param=enter).
  Verified: a 4K (3840x2160) H.264 clip plays in-app at ~25fps.
- Front/Rear segmented control swaps sides without leaving the screen.
- Buffering spinner, error -> Retry, Ended -> Replay, tap video to pause/resume.
- If a file is already downloaded (DownloadStatus.COMPLETED), the card shows "Downloaded",
  "Play offline" plays the LOCAL file (file://, no network), and "Open" launches an external
  chooser via FileProvider with MIME video/mp2t (.ts kept as-is, never renamed). Verified on
  device: local 4K playback + external chooser (MX Player, Video Player, Files, ...).
- File-card actions are Play / Download|Open (weighted) + a compact delete icon (no overlap).

New API surface: DashcamRepository.enterPlayback() (mirrors exitPlayback).

Known follow-up: MediaFilePlayerController builds LibVLC/Media on the main dispatcher (same
pattern as RtspLivePreviewController) -> a StrictMode main-thread I/O log (penaltyLog, not a
crash). Move construction to IO to fully honor the no-main-thread-work rule.

Remaining revamp: Phase 2 Transfers UX (send-to-transfers animation, in-app playback from the
Transfers list), Phase 3 visual revamp (theme switch replacing the "Dashcam" header, settings
redesign, live-overlay cleanup, Material 3 Expressive polish, overlap audit).
```

## Original Next Agent Priority Checklist (items 3-6 now DONE)

1. Build the latest source after this spec update.

```text
./gradlew --no-configuration-cache :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug
```

2. Install on phone.

```text
adb -s 3344bd2b install -r app/build/outputs/apk/debug/app-debug.apk
```

3. Verify Live tab resume.

```text
Launch app on dashcam Wi-Fi.
Go Live -> Library -> Live.
Expected: last selected camera feed starts automatically.
```

4. Verify repeated camera switching.

```text
Switch F/R/F/R/F/R slowly and then quickly.
Expected: no overlapping switch sequences, no permanent dead feed.
If it still dies, inspect whether fresh LibVLC release/start logs are present and whether dashcam stops answering RTSP after switchcam.
```

5. Verify downloads.

```text
Tap a visible Library download icon.
Look for Queue file download, Replace download work, DownloadWorker starting, response, completed.
Check external files directory for final .ts files.
```

6. Verify thumbnail persistence.

```text
Open Library, wait for thumbnails.
Scroll down and back up.
Expected: already loaded real thumbnails remain; 1062-byte placeholders do not replace cached good images.
```

7. Update this file again after runtime verification with exact results.
