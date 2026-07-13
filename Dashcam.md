# Dashcam Implementation Audit Spec

Last updated: 2026-07-13

This document describes the Android app as it is currently implemented in this
repository. It is intended for external auditing. It is not an aspirational
product brief.

## 1. App Identity

Implemented Android app:

```text
App name: Dashcam
Namespace: com.densmac.dashcam
Application ID: com.densmac.dashcam
Version code: 1
Version name: 1.0
Minimum SDK: 26
Compile SDK: 37
Target SDK: 37
UI toolkit: Jetpack Compose
Architecture style: MVVM with repositories and use cases
Dependency injection: Hilt
Local database: Room
Preferences: DataStore
Background downloads: WorkManager
HTTP client: OkHttp + Retrofit + Gson converter
Image loading: no external image-loading dependency is packaged; dashcam library/detail previews are native Compose icon tiles.
Live preview engine: Media3 ExoPlayer RTSP
```

Relevant files:

```text
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/densmac/dashcam/MainActivity.kt
app/src/main/java/com/densmac/dashcam/DashcamApp.kt
```

The app is a native Android app. It is not a WebView wrapper.

## 2. Privacy And External Services

Implemented:

```text
No Firebase
No backend server
No login or account creation
No analytics SDK
No crash reporting SDK
No ads
No remote config
No cloud sync
No internet service dependency beyond the local dashcam HTTP/RTSP endpoint
Android app backup is disabled with `allowBackup=false`.
Backup and data extraction XML explicitly exclude app files, databases, preferences, and external app data.
```

The app talks to the dashcam over local Wi-Fi:

```text
Dashcam SSID: DASHCAM
Dashcam host: 192.168.169.1
HTTP base URL: http://192.168.169.1/
RTSP preview URL: rtsp://192.168.169.1:554/track2
```

Android permissions declared:

```text
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
CHANGE_NETWORK_STATE
FOREGROUND_SERVICE
FOREGROUND_SERVICE_DATA_SYNC
POST_NOTIFICATIONS
```

Additional permissions present in the merged debug manifest through AndroidX/WorkManager:

```text
WAKE_LOCK
RECEIVE_BOOT_COMPLETED
com.densmac.dashcam.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

Broad shared-storage permissions are not requested. Downloads are saved under
the app-specific external files directory.

## 3. Current Navigation And UI

Implemented navigation:

```text
MainTabs route
Live tab
Library tab
Downloads tab
Settings route
Clip detail route
```

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/navigation/AppNavGraph.kt
app/src/main/java/com/densmac/dashcam/ui/navigation/Routes.kt
```

The main app shell uses:

```text
Three horizontally swipeable bottom tabs: Live, Library, Downloads
Icon-only bottom dock to avoid label overflow
Centered app title in the header
Settings icon on the top right
Round app mark on the top left
Centered page titles for Library, Downloads, Settings, and Clip detail
Warm clay/amber/cream palette with black-olive surfaces
System light/dark theme awareness
Optional dynamic color when enabled in settings
Minimal text in empty states
```

The bottom tab chips are functional navigation controls, not cosmetic elements.
The selected tab is indicated by the warm active capsule and icon color.

## 4. Theme Implementation

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/core/design/theme/Theme.kt
app/src/main/java/com/densmac/dashcam/core/design/theme/Color.kt
app/src/main/java/com/densmac/dashcam/data/datastore/UserPreferences.kt
app/src/main/java/com/densmac/dashcam/data/datastore/UserPreferencesDataSource.kt
```

Implemented theme modes:

```text
SYSTEM
LIGHT
DARK
```

Default mode:

```text
SYSTEM
```

Theme behavior:

```text
ThemeMode.SYSTEM follows Android system light/dark mode.
ThemeMode.LIGHT forces the light color scheme.
ThemeMode.DARK forces the dark color scheme.
Dynamic color is supported on Android S+ when the user enables it.
The app-specific warm palette is used when dynamic color is disabled.
Custom backgrounds and dock surfaces adapt based on the active color scheme.
```

Note for auditors: a previously saved explicit Light or Dark preference will
override system mode until the user changes Theme back to System in Settings.

## 5. Dependency Injection And Data Boundaries

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/core/network/NetworkModule.kt
```

Provided singletons and bindings include:

```text
ConnectivityManager
OkHttp client for dashcam API
OkHttp client for downloads
Retrofit DashcamApi
Room DashcamDatabase
WorkManager
DashcamNetworkBinder
DashcamConnectionMonitor
DashcamRepository
FileRepository
SettingsRepository
DownloadRepository
LivePreviewEngine
DispatchersProvider
```

No repository should expose raw Retrofit or OkHttp exceptions to UI state.
Endpoint failures are converted to `AppResult`.
DashcamRepository, FileRepository, and SettingsRepository bind to the verified
dashcam Wi-Fi network before their public dashcam API calls.

## 6. Error Handling

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/core/common/AppResult.kt
app/src/main/java/com/densmac/dashcam/core/common/AppError.kt
app/src/main/java/com/densmac/dashcam/data/api/DashcamApiMapper.kt
```

Implemented result model:

```text
AppResult.Success<T>
AppResult.Failure(AppError)
```

Implemented error mapping:

```text
result == 0 -> success
result == 98 -> UnsupportedEndpoint
other result -> ApiError(result, info)
SocketTimeoutException -> DashcamApiUnreachable
IOException -> DashcamApiUnreachable
HttpException -> HttpError
JsonParseException -> ParseError
other Throwable -> Unknown
CancellationException is rethrown
```

Operational error messages are centralized through `AppError.userMessage()`.
Reusable operation result notices are centralized through `AppNotice.userMessage()`.
Static UI labels, section titles, and confirmation dialog copy remain local to composables.

## 7. Dashcam Protocol Constants

Relevant file:

```text
app/src/main/java/com/densmac/dashcam/core/common/DashcamConstants.kt
```

Implemented constants:

```text
DEFAULT_HOST = 192.168.169.1
HTTP_BASE_URL = http://192.168.169.1/
RTSP_TRACK2_URL = rtsp://192.168.169.1:554/track2
RTSP_ROOT_URL = rtsp://192.168.169.1:554
DASHCAM_SSID = DASHCAM
HTTP_TIMEOUT_SECONDS = 8
DOWNLOAD_TIMEOUT_SECONDS = 30
STREAM_START_TIMEOUT_MS = 5000
DEVICE_POLL_INTERVAL_MS = 2500
LIVE_RECONNECT_DELAY_MS = 1500
FILE_PAIR_TOLERANCE_SECONDS = 3
```

Implemented endpoint constants:

```text
app/getdeviceattr
app/getproductinfo
app/capability
app/getmediainfo
app/playback
app/setting
app/enterrecorder
app/getparamitems
app/getparamvalue
app/setparamvalue
app/getsdinfo
app/getrecduration
app/getbatteryinfo
app/snapshot
app/getfilelist
app/getthumbnail
app/deletefile
```

## 8. Dashcam API Interface

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/data/api/DashcamApi.kt
app/src/main/java/com/densmac/dashcam/data/api/DashcamDtos.kt
app/src/main/java/com/densmac/dashcam/data/api/DashcamApiMapper.kt
```

Implemented API calls:

```text
GET /app/getdeviceattr
GET /app/getproductinfo
GET /app/capability
GET /app/getmediainfo
GET /app/playback?param=<enter|exit>
GET /app/setting?param=<enter|exit>
GET /app/enterrecorder
GET /app/getparamitems?param=all
GET /app/getparamvalue?param=all
GET /app/getparamvalue?param=<param>
GET /app/setparamvalue?param=<param>&value=<int>
GET /app/getsdinfo
GET /app/getrecduration
GET /app/getbatteryinfo
GET /app/snapshot
GET /app/getfilelist?folder=<folder>&start=<start>&end=<end>
GET /app/getthumbnail?file=<fullPath>
GET /app/deletefile?file=<fullPath>
```

The code contains a generic `DashcamEnvelope<T>` type, but the actual Retrofit
interface uses endpoint-specific DTO response classes.

Retrofit uses `GsonConverterFactory`.

## 9. Network Binding And Connection Monitoring

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/core/network/DashcamNetworkBinderImpl.kt
app/src/main/java/com/densmac/dashcam/core/network/DashcamConnectionMonitorImpl.kt
```

Implemented network binding:

```text
Enumerates all active Android networks.
Filters Wi-Fi networks.
Prefers networks whose link address starts with 192.168.169.* or gateway is 192.168.169.1.
Verifies the candidate by opening http://192.168.169.1/app/getdeviceattr.
Accepts the network only if the response contains "result":0.
Binds the process to the verified network with ConnectivityManager.bindProcessToNetwork.
Provides unbind support.
```

Implemented connection monitor:

```text
Singleton monitor running on IO dispatcher.
Monitoring is reference-counted so multiple ViewModels can share it safely.
Poll interval: 2500 ms.
State flow emits Unknown, Searching, Connected, or ApiUnreachable.
probeOnce() sets Searching and calls DashcamRepository.detectDevice().
DashcamRepository.detectDevice() performs the verified dashcam network bind.
LiveViewModel and SettingsViewModel start monitoring when created and release one reference on clear.
```

## 10. Device Detection

Relevant file:

```text
app/src/main/java/com/densmac/dashcam/data/repository/DashcamRepositoryImpl.kt
```

Implemented behavior:

```text
Binds to verified dashcam Wi-Fi before API calls.
Calls getdeviceattr.
Calls getproductinfo opportunistically.
Maps both into DashcamDevice.
Stores last seen device in Room devices table.
Stores last known UUID in DataStore.
Product info failure does not fail device detection.
```

Stored device fields:

```text
uuid
model
softwareVersion
hardwareVersion
ssid
cameraCount
soc
lastSeenAt
```

## 11. Live Tab

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/screens/live/LiveScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/live/LiveViewModel.kt
app/src/main/java/com/densmac/dashcam/domain/usecase/PrepareLivePreviewUseCase.kt
app/src/main/java/com/densmac/dashcam/domain/usecase/SwitchCameraUseCase.kt
app/src/main/java/com/densmac/dashcam/core/player/RtspLivePreviewController.kt
```

Implemented UI:

```text
Large preview-first live panel.
Connection pill opens Android Wi-Fi settings panel when disconnected.
Embedded preview controls: snapshot, record toggle, reconnect, library.
Embedded camera selector: F and R.
Minimal storage strip linking to Library.
Icon-only controls.
```

Implemented preview start sequence:

```text
Bind process to dashcam Wi-Fi.
Exit playback mode.
Exit settings mode.
Enter recorder mode.
Call getmediainfo opportunistically.
Start RTSP playback at rtsp://192.168.169.1:554/track2.
```

Implemented RTSP player behavior:

```text
Media3 ExoPlayer.
RtspMediaSource.
RTP over TCP forced.
Audio track disabled.
No PlayerView controller.
One player instance at a time, protected by Mutex.
Start timeout: 5000 ms.
Player released on stop/release/onCleared.
Player is also stopped and released when RTSP startup times out.
Player state exposed as LivePreviewState.
```

No-feed visual behavior:

```text
When LivePreviewState is not Playing, the Live panel renders an animated grain-only static surface.
The no-feed effect does not draw scanlines, rolling bands, or vertical scrolling artifacts.
Connection, camera, preview, action, and Vault controls are overlaid above that static surface.
When LivePreviewState is Playing, the static surface is removed and the RTSP PlayerView is visible.
```

Implemented camera switching:

```text
Stops RTSP player.
Calls setting enter.
Calls setparamvalue switchcam with camera value.
Applies cameraMappingSwapped when choosing the switchcam value.
Persists the selected camera as preferredCamera after a successful dashcam switch.
Waits 300 ms.
Calls enterrecorder.
Restarts RTSP at the same track2 URL.
```

Camera values:

```text
Default mapping: FRONT -> 0, REAR -> 1.
Swapped mapping: FRONT -> 1, REAR -> 0.
```

Implemented status loading:

```text
LiveViewModel starts connection monitoring.
Dashcam storage/settings are loaded only after a connected device UUID is known.
Manual refresh probes connection first, then loads storage/settings.
This avoids eager storage/settings calls before the dashcam is reachable.
preferredCamera is collected from DataStore and reflected in LiveUiState.
If autoStartLivePreview is enabled, Live starts preview once per connected device UUID.
Manual and automatic start always apply the preferred camera by sending the switchcam command after preview preparation, including FRONT.
```

Implemented recording toggle:

```text
Starting recording calls setparamvalue rec=1.
Stopping recording requires confirmation, then calls setparamvalue rec=0.
After setting rec, settings are refreshed after 300 ms.
```

Implemented snapshot:

```text
Calls /app/snapshot.
Shows success/failure message in state.
Does not currently auto-open or auto-refresh the Event folder UI.
```

## 12. Library Tab

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/screens/library/LibraryScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/library/LibraryViewModel.kt
app/src/main/java/com/densmac/dashcam/data/repository/FileRepositoryImpl.kt
app/src/main/java/com/densmac/dashcam/domain/usecase/GetFileBundlesUseCase.kt
```

Implemented UI:

```text
Centered "Vault" page title.
Refresh icon on the right.
Functional folder selector row.
Icon-only empty/offline state.
Grid of visual file cards when files are available.
Card tap opens clip detail.
Long press toggles selection mode.
Selected files can be deleted after confirmation.
Per-card download and delete icon controls.
```

Folder selectors are functional:

```text
Loop -> DashcamFolder.LOOP -> folder=loop, range 0..199
Event -> DashcamFolder.EVENT -> folder=event, range 0..99
Park -> DashcamFolder.PARK -> folder=park, range 0..99
SOS -> DashcamFolder.EMR -> folder=emr, range 0..99
Race -> DashcamFolder.RACE -> folder=race, range 0..99
```

Tapping a folder calls:

```text
LibraryViewModel.load(folder)
```

Which then:

```text
Sets loading true.
Clears message and selected IDs.
Calls GetFileBundlesUseCase(folder).
Updates current folder and bundles on success.
Stores error message in state on failure.
```

File listing behavior:

```text
Binds to verified dashcam Wi-Fi before dashcam API calls.
Calls /app/playback?param=enter before listing.
Calls /app/getfilelist with folder/start/end.
Maps each file item to DashcamFile.
The /app/getthumbnail endpoint is modeled in DashcamConstants and DashcamApi.
Library and clip detail do not load remote thumbnails through Coil; they render native icon-preview tiles.
```

File pairing behavior:

```text
Groups by media type.
Sorts front and rear files by camera local time descending.
Pairs nearest rear file to each front file when timestamps differ by <= 3 seconds.
Keeps unmatched front, rear, and unknown camera files as individual bundles.
Sorts bundles by start time descending.
Bundle ID format: <folder>_<timestamp>_<mediaType>.
If no parsed start timestamp exists, the middle key falls back to the primary filename.
```

Delete behavior:

```text
Calls /app/deletefile once per selected file path.
Waits 150 ms between delete calls.
Reports deleted and failed counts.
Reloads the current folder after delete.
Delete always requires confirmation.
```

## 13. Clip Detail Route

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/screens/detail/ClipDetailScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/detail/ClipDetailViewModel.kt
```

Implemented behavior:

```text
Loads a bundle based on encoded bundle ID.
Displays native icon-preview hero instead of directly fetching remote thumbnails.
Displays date/time, folder, and total size.
Shows front and rear file cards.
Allows downloading front, rear, or both.
Allows deleting front, rear, or both with confirmation.
```

Not currently implemented in clip detail:

```text
Inline remote video playback.
Inline local downloaded video playback.
Full-screen playback UI.
```

## 14. Downloads Tab

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/screens/downloads/DownloadsScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/downloads/DownloadsViewModel.kt
app/src/main/java/com/densmac/dashcam/data/repository/DownloadRepositoryImpl.kt
app/src/main/java/com/densmac/dashcam/data/download/DashcamDownloadManager.kt
app/src/main/java/com/densmac/dashcam/data/download/DownloadWorker.kt
```

Implemented UI:

```text
Centered "Transfers" title.
Icon-only empty state when there are no local downloads.
List of download cards when downloads exist.
Status dot shows status initial.
Icon actions for retry, cancel, and delete local.
Delete local opens a confirmation dialog before repository deletion.
```

Implemented download queue behavior:

```text
Downloads are represented in Room before WorkManager starts.
Stable download ID is first 24 hex chars of SHA-256(remotePath).
If an item is already queued, running, or completed, enqueue returns success without duplicating.
Bundle download enqueues front and rear files independently.
WorkManager unique work name is the stable download ID.
Initial enqueue uses ExistingWorkPolicy.KEEP.
Retry uses ExistingWorkPolicy.REPLACE.
Cancel cancels unique work and marks status CANCELLED.
Retry, cancel, and delete local return AppResult and catch Room/WorkManager exceptions.
Local delete wraps the Room lookup, work cancellation, file deletion, and row deletion in one failure-mapped operation.
Delete local marks active queued/running/paused work CANCELLED, then calls WorkManager cancel and waits for the cancellation Operation before deleting files.
Delete local removes final and .partial files only after confirmation.
Delete local returns failure if either existing file cannot be deleted, and keeps the Room row in that case.
```

Local path behavior:

```text
Root: context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)/Dashcam
Loop folder: Recordings
Event folder: Snapshots
Other folders: folder.displayName
Filenames are sanitized.
Camera suffix is normalized to front/rear/unknown.
```

Worker behavior:

```text
Binds to dashcam network before HTTP download.
If binding fails, marks FAILED with the bind error message and returns Result.retry().
Uses ForegroundInfo with FOREGROUND_SERVICE_TYPE_DATA_SYNC.
Merges WorkManager SystemForegroundService with android:foregroundServiceType="dataSync".
Downloads from HTTP_BASE_URL + remotePath without leading slash.
Uses .partial temporary file.
Supports HTTP Range resume when .partial exists.
If server ignores Range and returns 200, verifies partial delete before restarting full download.
Writes using 128 KiB buffer.
Initial RUNNING status and foreground notification setup are inside the same exception handling path as the download.
Updates foreground notification and Room progress every 512 KiB.
Checks cancellation before reads, before final file replacement, and before marking COMPLETED.
RUNNING, progress, FAILED, and COMPLETED worker writes use conditional DAO updates that do not overwrite CANCELLED rows.
Marks COMPLETED only after .partial is renamed to final file.
Marks FAILED and returns Result.retry() on exceptions or non-success HTTP response.
Explicit user cancellation is preserved as CANCELLED and is not overwritten as FAILED.
Worker cancellation caused by retry/replace does not overwrite the new queued work as CANCELLED.
```

## 15. Settings Route

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/ui/screens/settings/SettingsScreen.kt
app/src/main/java/com/densmac/dashcam/ui/screens/settings/SettingsViewModel.kt
app/src/main/java/com/densmac/dashcam/data/repository/SettingsRepositoryImpl.kt
```

Implemented app preferences:

```text
Theme mode: SYSTEM, LIGHT, DARK
Dynamic color enabled/disabled
Haptics enabled/disabled
Auto-start live preview flag
Debug diagnostics flag
Camera mapping swapped flag
Preferred camera
Last known device UUID
```

Implemented dashcam settings read:

```text
Binds to verified dashcam Wi-Fi before dashcam API calls.
Calls /app/setting?param=enter.
Calls /app/getparamitems?param=all.
Calls /app/getparamvalue?param=all.
Only maps settings present in both supported items and current values, except rec.
rec_resolution maps only when the current rec_resolution value matches an index entry from supported items.
```

Implemented safe dashcam settings writes:

```text
mic
osd
speaker
gsr_sensitivity
rec_split_duration
rec
timelapse_rate
switchcam
```

Unsupported parameter writes are rejected as `UnsupportedEndpoint`.
Settings writes bind to verified dashcam Wi-Fi before entering settings mode.
switchcam writes apply the cameraMappingSwapped preference.

## 16. Persistence

Relevant files:

```text
app/src/main/java/com/densmac/dashcam/data/db/DashcamDatabase.kt
app/src/main/java/com/densmac/dashcam/data/db/DeviceEntity.kt
app/src/main/java/com/densmac/dashcam/data/db/DownloadEntity.kt
app/src/main/java/com/densmac/dashcam/data/datastore/UserPreferences.kt
```

Room database:

```text
Name: dashcam.db
Version: 1
Export schema: false
Entities: downloads, devices
fallbackToDestructiveMigration(false)
```

Downloads table fields:

```text
id
remotePath
localPath
folder
camera
mediaType
status
bytesDownloaded
totalBytes
createdAt
updatedAt
errorMessage
```

Devices table fields:

```text
uuid
model
softwareVersion
hardwareVersion
ssid
cameraCount
soc
lastSeenAt
```

DataStore name:

```text
user_preferences
```

## 17. Safety Constraints Implemented

Implemented safeguards:

```text
No risky SD-card format operation.
No Wi-Fi SSID/password change.
No firmware update.
No lock/protect/unlock video operation.
Delete requires confirmation.
Repository/API calls return AppResult.
Network and file IO use IO dispatcher or WorkManager.
RTSP player lifecycle is released on stop/release/ViewModel clear.
RTSP start is mutex-protected to avoid concurrent player sessions.
Downloads persist across process death through Room and WorkManager.
Unique WorkManager names prevent duplicate active download work for the same file.
```

## 18. Re-Audit Closure Summary

The following audit-sensitive gaps have been closed in the current implementation:

```text
DashcamRepository, FileRepository, and SettingsRepository bind to verified dashcam Wi-Fi before public dashcam API calls.
DownloadWorker checks dashcam Wi-Fi binding before HTTP download and fails/retries cleanly when binding fails.
Download foreground work uses dataSync service typing in ForegroundInfo and the WorkManager foreground service manifest merge.
Download retry, cancel, and local delete return AppResult and catch Room/WorkManager failures.
Explicit download cancellation remains CANCELLED and is not overwritten as FAILED by the worker.
The worker checks cancellation before reads, before final rename, and before marking COMPLETED.
Worker status/progress writes are conditional and cannot overwrite CANCELLED rows.
Local download delete cancels the unique WorkManager work and waits for WorkManager cancellation before deleting local files/Room rows.
Local download delete requires confirmation and verifies final and .partial file deletion before removing the Room row.
UserPreferences contains only wired app preferences; no unused preferredStartScreen field or DataStore key remains.
Remote thumbnails are not fetched directly by Coil; Library and Clip detail use native Compose icon-preview tiles.
Coil has been removed from app dependencies.
/app/getthumbnail is modeled in DashcamConstants and DashcamApi for future repository-controlled use.
DashcamNetworkBinder's direct /app/getdeviceattr validation probe is documented separately from repository-managed feature API access.
Operational error/result notices are centralized through AppError/AppNotice message helpers; static UI copy remains local to composables.
preferredCamera, autoStartLivePreview, and cameraMappingSwapped are functionally applied in Live camera behavior.
Live preview start applies preferredCamera even when the preference is FRONT.
RTSP startup timeout releases the allocated player.
DashcamConnectionMonitor is reference-counted for multiple active ViewModel consumers.
Android backup/cloud extraction is disabled or explicitly excluded.
HTTP timeout constants are used by injected OkHttp clients.
LocalFileIntentViewer is named according to its ACTION_VIEW external viewer behavior.
Haptics are preference-aware on primary tab/settings navigation.
Live no-feed rendering is grain-only static behind overlaid controls, without scanlines or vertical scrolling artifacts.
DownloadWorker initial RUNNING status and ForegroundInfo setup are inside the documented exception/failure handling path.
rec_resolution display is derived from current value plus supported index mapping, not from the first supported label alone.
```

## 19. Known Non-Goals Or Not Yet Wired

Not implemented:

```text
Backend, login, account, analytics, ads, cloud sync.
Shared/public media store export.
Remote or local full video playback screen.
Lock/protect/unlock video.
SD-card format.
Wi-Fi credential management.
Firmware update.
Automatic Event folder refresh after snapshot.
Tablet-specific adaptive multi-pane layout.
Landscape-specific live preview layout.
Launcher icon redesign beyond default Android launcher resources.
```

Partially implemented:

```text
LocalFileIntentViewer exists for external ACTION_VIEW playback, but clip detail does not wire it into inline UI playback.
Haptics are preference-aware on primary tab/settings navigation; they are not applied to every control.
Theme settings exist and system light/dark is restored; previously saved explicit Light/Dark preferences still override System until changed by the user.
```

## 20. Build And Verification State

Most recent verification performed during this implementation pass:

```text
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -S -W -n com.densmac.dashcam/.MainActivity
```

Observed launch result on emulator-5554:

```text
Status: ok
LaunchState: COLD
Activity: com.densmac.dashcam/.MainActivity
TotalTime: 1588 ms
```

Physical device `3344bd2b` was not attached during this pass; only `emulator-5554` was available through adb.

## 21. Auditor Checklist

External auditors should verify:

```text
No network endpoints exist outside 192.168.169.1 for app functionality.
No Firebase, analytics, crash reporting, ads, or login libraries are included.
No broad storage permissions are declared.
Merged WorkManager permissions are documented separately from source manifest permissions.
Android backup/cloud extraction is disabled or explicitly excluded.
Feature-level dashcam API access routes through DashcamApi and repository/use-case layers.
DashcamNetworkBinder's documented verification probe calls /app/getdeviceattr directly before binding to the validated network.
DashcamRepository, FileRepository, SettingsRepository, and DownloadWorker bind before dashcam calls/downloads.
Download foreground service type is declared as dataSync in ForegroundInfo and manifest.
Library and clip detail do not perform direct Coil thumbnail HTTP requests.
Coil is not packaged as an app dependency.
Delete remains confirmation-gated.
Local download delete is also confirmation-gated and checks file deletion results.
Download cancellation remains CANCELLED after explicit user cancel.
Download worker setup exceptions are handled by the same failure/retry path as download exceptions.
Download worker does not mark COMPLETED after cancellation before finalization.
Local download deletion cancels unique work and waits for cancellation before file and row deletion.
Downloads are stored in app-specific external files.
ThemeMode.SYSTEM follows Android system light/dark mode.
preferredCamera, autoStartLivePreview, and cameraMappingSwapped affect Live behavior.
Live start sends switchcam for the preferred camera even when preferredCamera is FRONT.
rec_resolution display requires both a current value and a matching supported index.
Live no-feed effect is grain-only static with controls overlaid above it.
Folder chips in Library call LibraryViewModel.load(folder) and are functional.
RTSP player is released on screen disposal/ViewModel clear and startup timeout.
WorkManager and Room preserve download state across process death.
```
