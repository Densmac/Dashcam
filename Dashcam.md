# Dashcam.md

## 1. Purpose

Build a private Android dashcam companion app for the YantopCam / Viidure-compatible dashcam currently tested on the user's device. The app is for private consumption only. It must not include a backend, login, Firebase, analytics, cloud sync, account creation, telemetry, ads, or any internet service dependency.

The app must be beautiful, fast, stable, and resilient. It must use Kotlin, Jetpack Compose, MVVM, Hilt dependency injection, Material 3 / Material 3 Expressive styling, light and dark mode, thoughtful animations, thoughtful haptics, and strict defensive error handling.

The app must target the confirmed dashcam protocol:

```text
Dashcam Wi-Fi SSID observed: DASHCAM
Dashcam IP: 192.168.169.1
HTTP API base: http://192.168.169.1
RTSP live preview: rtsp://192.168.169.1:554/track2
HTTP control port: 80
RTSP port: 554
Additional open service observed: 5000
Device model: YantopCam
SOC/platform: eeasytech / FH
Camera count: 2
Saved front video: 3840x2160 H.264 .ts
Saved rear video: 1920x1080 H.264 .ts
Live preview: 800x480 H.264 at 30 fps over RTSP/TCP
```

The app must be implemented as a proper local Android app, not as a WebView wrapper.

---

## 2. Non-negotiable constraints

### 2.1 Architecture constraints

Use:

```text
Kotlin
Jetpack Compose
Material 3 / Material 3 Expressive visual language
MVVM
Hilt DI
Coroutines + Flow
Room for local metadata
DataStore for preferences
OkHttp + Retrofit for dashcam HTTP API
Coil for thumbnails/images
Media3 for local file playback
Robust RTSP player abstraction for live preview
```

Do not use:

```text
Firebase
Backend server
Login/account system
Cloud sync
Analytics
Crash reporting SDKs
Ads
Remote config
GlobalScope
Blocking network calls on main thread
Broad storage permissions
Hidden internet dependency
```

### 2.2 Stability constraints

The implementation must actively prevent:

```text
ANR
Memory leaks
OutOfMemoryError from thumbnails or video
Unhandled coroutine exceptions
Unhandled networking exceptions
Process death losing download state
RTSP player leaks
Activity/context leaks
Blocking file IO on the main thread
Multiple simultaneous live-preview sessions
Multiple destructive operations at once
```

Every repository call must return a typed result, not throw raw exceptions to the UI layer.

### 2.3 Safety constraints

This is a real dashcam with real recordings. Do not add risky features unless explicitly listed in the MVP.

The following features must be excluded from MVP and kept only as future TODOs:

```text
Lock/protect video
Unlock video
Format SD card
Change Wi-Fi password
Change Wi-Fi SSID
Firmware update
```

The app may include delete because the endpoint has been confirmed, but delete must require explicit user confirmation.

---

## 3. App identity

Use these defaults unless the user changes them later:

```text
App display name: Dashcam
Package name: com.densmac.dashcam
Minimum SDK: 26
Target SDK: latest installed stable SDK in the project environment
Orientation: portrait first, landscape supported for live preview
Form factors: phone first, tablet adaptive layout supported
```

Launcher icon concept:

```text
Rounded square icon.
Dark graphite background.
Minimal camera lens outline.
Small lime/green recording dot.
No brand logos copied from Yantop or Viidure.
```

---

## 4. Confirmed protocol specification

### 4.1 Base constants

Create `DashcamConstants.kt`:

```kotlin
object DashcamConstants {
    const val DEFAULT_HOST = "192.168.169.1"
    const val HTTP_BASE_URL = "http://192.168.169.1/"
    const val RTSP_TRACK2_URL = "rtsp://192.168.169.1:554/track2"
    const val RTSP_ROOT_URL = "rtsp://192.168.169.1:554"
    const val DASHCAM_SSID = "DASHCAM"

    const val HTTP_TIMEOUT_SECONDS = 8L
    const val STREAM_START_TIMEOUT_MS = 5_000L
    const val DEVICE_POLL_INTERVAL_MS = 2_500L
    const val LIVE_RECONNECT_DELAY_MS = 1_500L
    const val FILE_PAIR_TOLERANCE_SECONDS = 3L
}
```

Do not scatter endpoint strings throughout the codebase. All endpoint paths must be declared in one API interface or constants file.

### 4.2 API response envelope

All JSON control endpoints use this broad envelope:

```json
{
  "result": 0,
  "info": {}
}
```

Interpretation:

```text
result == 0: success
result == 98: unsupported endpoint, unavailable feature, or unavailable module
other result: failure/unknown error
```

Create:

```kotlin
@Serializable
data class DashcamEnvelope<T>(
    val result: Int,
    val info: T? = null
)
```

If Retrofit/Moshi/Kotlinx serialization cannot decode a variable `info` type, create endpoint-specific DTOs instead of using unsafe `Any`.

### 4.3 Confirmed endpoints

All confirmed endpoints use HTTP `GET`.

#### Device detection

```http
GET /app/getdeviceattr
```

Confirmed response shape:

```json
{
  "result": 0,
  "info": {
    "uuid": "GBB88102",
    "softver": "20241030.093910",
    "hwver": "V3.00-WIFI-ADAS-RC6-20220909",
    "ssid": "DASHCAM",
    "bssid": "DASHCAM",
    "camnum": 2,
    "curcamid": 0
  }
}
```

DTO:

```kotlin
data class DeviceAttrDto(
    val uuid: String,
    val softver: String,
    val hwver: String,
    val ssid: String,
    val bssid: String,
    val camnum: Int,
    val curcamid: Int
)
```

#### Product info

```http
GET /app/getproductinfo
```

Confirmed response:

```json
{
  "result": 0,
  "info": {
    "model": "YantopCam",
    "company": "FH",
    "soc": "eeasytech",
    "sp": "FH"
  }
}
```

DTO:

```kotlin
data class ProductInfoDto(
    val model: String,
    val company: String,
    val soc: String,
    val sp: String
)
```

#### Capability

```http
GET /app/capability
```

Observed:

```json
{"result":0,"info":{"value":"3000010"}}
```

Store as raw string. Do not infer feature flags yet.

#### Media info

```http
GET /app/getmediainfo
```

Observed:

```json
{
  "result": 0,
  "info": {
    "rtsp": "rtsp://192.168.169.1",
    "transport": "tcp",
    "port": 5000
  }
}
```

Important: although this reports `port: 5000`, the confirmed working live preview is:

```text
rtsp://192.168.169.1:554/track2
```

The MVP must use `rtsp://192.168.169.1:554/track2` for live preview.

#### Exit playback mode

```http
GET /app/playback?param=exit
```

Expected success:

```json
{"result":0,"info":"success"}
```

#### Enter playback mode

```http
GET /app/playback?param=enter
```

Use before file browsing if needed. If file listing works without this, still call it when entering the Library screen because Viidure does this pattern.

#### Exit settings mode

```http
GET /app/setting?param=exit
```

Expected success:

```json
{"result":0,"info":"success"}
```

#### Enter settings mode

```http
GET /app/setting?param=enter
```

Use before settings read/write when needed.

#### Enter recorder/live mode

```http
GET /app/enterrecorder
```

Expected success:

```json
{"result":0,"info":"enter recorder success"}
```

Call this immediately before starting RTSP live preview.

#### Get setting items

```http
GET /app/getparamitems?param=all
```

Confirmed response:

```json
{
  "result": 0,
  "info": [
    {"name":"mic","items":["off","on"],"index":[0,1]},
    {"name":"osd","items":["off","on"],"index":[0,1]},
    {"name":"rec_resolution","items":["4K+1080P"],"index":[3]},
    {"name":"rec_split_duration","items":["1MIN","2MIN","3MIN"],"index":[0,1,2]},
    {"name":"speaker","items":["off","low","middle","high"],"index":[0,1,2,3]},
    {"name":"gsr_sensitivity","items":["off","low","middle","high"],"index":[0,1,2,3]},
    {"name":"rec","items":["off","on"],"index":[0,1]},
    {"name":"timelapse_rate","items":["off","1S","2S","3S"],"index":[0,1,2,3]}
  ]
}
```

#### Get setting values

```http
GET /app/getparamvalue?param=all
```

Confirmed response:

```json
{
  "result": 0,
  "info": [
    {"name":"mic","value":1},
    {"name":"osd","value":1},
    {"name":"logo_osd","value":1},
    {"name":"rec_resolution","value":0},
    {"name":"rec_split_duration","value":2},
    {"name":"encodec","value":0},
    {"name":"speaker","value":0},
    {"name":"gsr_sensitivity","value":0},
    {"name":"rec","value":1},
    {"name":"park_record_time","value":0},
    {"name":"timelapse_rate","value":0}
  ]
}
```

Only render settings that are present in both supported items and current values, except `rec`, which may be read separately.

#### Get recording status

```http
GET /app/getparamvalue?param=rec
```

Observed shape:

```json
{"result":0,"info":{"value":1}}
```

Interpretation:

```text
0 = recording off
1 = recording on
```

#### Set parameter value

```http
GET /app/setparamvalue?param=<PARAM_NAME>&value=<INT_VALUE>
```

Supported safe params for MVP:

```text
mic: 0 off, 1 on
osd: 0 off, 1 on
speaker: 0 off, 1 low, 2 middle, 3 high
gsr_sensitivity: 0 off, 1 low, 2 middle, 3 high
rec_split_duration: 0 1MIN, 1 2MIN, 2 3MIN
rec: 0 stop recording, 1 start recording
timelapse_rate: 0 off, 1 1S, 2 2S, 3 3S
switchcam: 0 front/default, 1 rear/alternate
```

Do not set unsupported params.

#### Switch live camera

```http
GET /app/setparamvalue?param=switchcam&value=0
GET /app/setparamvalue?param=switchcam&value=1
```

Implementation rule:

```text
0 = Front by default
1 = Rear by default
```

After switching camera, stop and recreate the RTSP player. Do not rely on the existing RTSP session to change stream content cleanly.

#### SD card info

```http
GET /app/getsdinfo
```

Observed response:

```json
{
  "result": 0,
  "info": {
    "status": 0,
    "free": 16429,
    "total": 61048
  }
}
```

Interpretation:

```text
status 0 = normal/available
free and total are MB-ish values
```

Display as GB:

```kotlin
val totalGb = total / 1024.0
val freeGb = free / 1024.0
val usedGb = totalGb - freeGb
val usedPercent = if (total > 0) ((total - free).toFloat() / total.toFloat()) else 0f
```

Use one decimal place.

#### Get recording duration

```http
GET /app/getrecduration
```

Observed response:

```json
{"result":0,"info":{"duration":85}}
```

Treat duration as seconds of current segment/session. Display only if useful; not required for MVP home screen.

#### Battery info

```http
GET /app/getbatteryinfo
```

Observed response:

```json
{"result":0,"info":{"capacity":66,"charge":0}}
```

Do not present as car battery voltage. Label as `Internal battery/capacitor status` if shown.

#### Snapshot

```http
GET /app/snapshot
```

Expected success:

```json
{"result":0,"info":"snapshot success"}
```

After success, refresh `event` file list.

#### File list

```http
GET /app/getfilelist?folder=loop&start=0&end=199
GET /app/getfilelist?folder=event&start=0&end=99
GET /app/getfilelist?folder=park&start=0&end=99
GET /app/getfilelist?folder=emr&start=0&end=99
GET /app/getfilelist?folder=race&start=0&end=99
```

Known folder meanings:

```text
loop = normal loop recordings
 event = snapshots / event pictures
 park = parking recordings, may be empty
 emr = emergency recordings, may be empty
 race = race/special folder, may be empty
```

Observed file item shape:

```json
{
  "name": "/mnt/sdcard/VIDEO_F/20260713_192150_34_f.ts",
  "duration": -1,
  "size": 90112,
  "createtime": 1783970510,
  "createtimestr": "20260713192150",
  "type": 2
}
```

Interpretation:

```text
name = full dashcam file path
size = KB-like unit, not bytes
type 2 = video
type 1 = picture
duration -1 = duration unavailable from list
duration 0 = picture/no duration
createtimestr = local camera timestamp in yyyyMMddHHmmss
```

File path conventions:

```text
/mnt/sdcard/VIDEO_F/..._f.ts = front video
/mnt/sdcard/VIDEO_B/..._b.ts = rear video
/mnt/sdcard/PICTURE_F/..._f.jpg = front picture
/mnt/sdcard/PICTURE_B/..._b.jpg = rear picture
```

#### Thumbnail

```http
GET /app/getthumbnail?file=<URL_ENCODED_FULL_FILE_PATH>
```

Example:

```http
GET /app/getthumbnail?file=/mnt/sdcard/VIDEO_F/20260713_192150_34_f.ts
```

Implementation rule: URL-encode the full file path but keep `/` unescaped if the networking library supports it safely. If unsure, use standard query parameter encoding through Retrofit/OkHttp rather than string concatenation.

#### Direct file download

Direct HTTP download uses the file path as the URL path:

```http
GET /mnt/sdcard/VIDEO_F/<filename>.ts
GET /mnt/sdcard/VIDEO_B/<filename>.ts
GET /mnt/sdcard/PICTURE_F/<filename>.jpg
GET /mnt/sdcard/PICTURE_B/<filename>.jpg
```

Range downloads are supported or at least tolerated by the camera. Implement resumable downloads using `Range: bytes=<existing>-`. If the server responds with `200` instead of `206`, discard the partial file and restart from byte 0.

#### GPS file

The camera has been seen requesting:

```http
GET /GPSdata/<matching_filename>.TXT
```

However, the tested device returns:

```json
{"result":98}
```

MVP rule: GPS support must be optional and hidden by default. If a GPS file request returns `result:98`, mark GPS unavailable and do not show an error toast.

#### Delete file

Confirmed endpoint:

```http
GET /app/deletefile?file=<FULL_FILE_PATH>
```

Example:

```http
GET /app/deletefile?file=/mnt/sdcard/PICTURE_B/20260713_192539_31_b.jpg
```

Observed success:

```json
{"result":0,"info":"delete success"}
```

Deletion rules:

```text
Never delete automatically.
Always show a confirmation dialog.
For paired front/rear clips, offer:
  - Delete front only
  - Delete rear only
  - Delete both
After delete success, refresh the relevant folder list.
If deleting both and one fails, show partial success with exact file status.
```

---

## 5. Android project structure

Create the following package structure:

```text
com.densmac.dashcam
├── DashcamApp.kt
├── MainActivity.kt
├── core
│   ├── common
│   │   ├── DashcamConstants.kt
│   │   ├── DispatchersProvider.kt
│   │   ├── AppResult.kt
│   │   ├── AppError.kt
│   │   ├── DateTimeFormatters.kt
│   │   └── Logger.kt
│   ├── design
│   │   ├── theme
│   │   │   ├── Color.kt
│   │   │   ├── Theme.kt
│   │   │   ├── Type.kt
│   │   │   ├── Shapes.kt
│   │   │   └── Motion.kt
│   │   ├── components
│   │   │   ├── GlassCard.kt
│   │   │   ├── DashcamButton.kt
│   │   │   ├── StatusPill.kt
│   │   │   ├── SectionHeader.kt
│   │   │   ├── ConfirmDangerDialog.kt
│   │   │   ├── LoadingShimmer.kt
│   │   │   └── EmptyState.kt
│   │   └── haptics
│   │       └── HapticFeedbackManager.kt
│   ├── network
│   │   ├── NetworkModule.kt
│   │   ├── DashcamNetworkBinder.kt
│   │   ├── DashcamConnectionMonitor.kt
│   │   └── CleartextPolicy.md
│   └── player
│       ├── LivePreviewEngine.kt
│       ├── LivePreviewState.kt
│       ├── RtspLivePreviewController.kt
│       ├── Media3LocalPlaybackController.kt
│       └── PlayerModule.kt
├── data
│   ├── api
│   │   ├── DashcamApi.kt
│   │   ├── DashcamDtos.kt
│   │   └── DashcamApiMapper.kt
│   ├── db
│   │   ├── DashcamDatabase.kt
│   │   ├── DownloadEntity.kt
│   │   ├── DeviceEntity.kt
│   │   └── dao
│   │       ├── DownloadDao.kt
│   │       └── DeviceDao.kt
│   ├── datastore
│   │   ├── UserPreferences.kt
│   │   └── UserPreferencesDataSource.kt
│   ├── repository
│   │   ├── DashcamRepositoryImpl.kt
│   │   ├── FileRepositoryImpl.kt
│   │   ├── SettingsRepositoryImpl.kt
│   │   └── DownloadRepositoryImpl.kt
│   └── download
│       ├── DashcamDownloadManager.kt
│       ├── DownloadWorker.kt
│       └── DownloadNotificationHelper.kt
├── domain
│   ├── model
│   │   ├── DashcamDevice.kt
│   │   ├── DashcamConnectionState.kt
│   │   ├── DashcamFile.kt
│   │   ├── DashcamFolder.kt
│   │   ├── DashcamFileBundle.kt
│   │   ├── DashcamSettings.kt
│   │   ├── StorageStatus.kt
│   │   └── RecordingState.kt
│   ├── repository
│   │   ├── DashcamRepository.kt
│   │   ├── FileRepository.kt
│   │   ├── SettingsRepository.kt
│   │   └── DownloadRepository.kt
│   └── usecase
│       ├── DetectDashcamUseCase.kt
│       ├── PrepareLivePreviewUseCase.kt
│       ├── SwitchCameraUseCase.kt
│       ├── GetFileBundlesUseCase.kt
│       ├── TakeSnapshotUseCase.kt
│       ├── DeleteFileUseCase.kt
│       ├── UpdateSettingUseCase.kt
│       └── StartStopRecordingUseCase.kt
└── ui
    ├── navigation
    │   ├── AppNavGraph.kt
    │   └── Routes.kt
    ├── screens
    │   ├── home
    │   │   ├── HomeScreen.kt
    │   │   ├── HomeViewModel.kt
    │   │   └── HomeUiState.kt
    │   ├── live
    │   │   ├── LiveScreen.kt
    │   │   ├── LiveViewModel.kt
    │   │   └── LiveUiState.kt
    │   ├── library
    │   │   ├── LibraryScreen.kt
    │   │   ├── LibraryViewModel.kt
    │   │   ├── LibraryUiState.kt
    │   │   └── components
    │   ├── detail
    │   │   ├── ClipDetailScreen.kt
    │   │   ├── ClipDetailViewModel.kt
    │   │   └── ClipDetailUiState.kt
    │   ├── downloads
    │   │   ├── DownloadsScreen.kt
    │   │   ├── DownloadsViewModel.kt
    │   │   └── DownloadsUiState.kt
    │   └── settings
    │       ├── SettingsScreen.kt
    │       ├── SettingsViewModel.kt
    │       └── SettingsUiState.kt
    └── previewdata
        └── PreviewFixtures.kt
```

---

## 6. Dependency guidance

Use a Gradle version catalog. Do not hard-code old versions. Use the latest stable compatible versions available in the current Android project environment.

Required dependency groups:

```text
AndroidX Core KTX
AndroidX Lifecycle Runtime KTX
AndroidX Lifecycle ViewModel Compose
AndroidX Activity Compose
Jetpack Compose BOM
Compose UI
Compose Foundation
Compose Material3
Compose Material Icons Extended
Compose Animation
Navigation Compose
Hilt Android
Hilt Navigation Compose
Kotlinx Coroutines Android
Kotlinx Serialization JSON or Moshi
Retrofit
OkHttp
OkHttp Logging Interceptor, debug only
Room Runtime
Room KTX
Room Compiler/KSP
DataStore Preferences
Coil Compose
Media3 ExoPlayer
Media3 UI
Media3 RTSP
WorkManager KTX
Hilt Work
Accompanist permissions only if needed; prefer native permission handling where possible
```

RTSP live preview must be behind `LivePreviewEngine`. The first implementation may use Media3 RTSP. If Media3 fails on the dashcam because of the malformed AAC stream, replace the engine implementation with LibVLC while keeping the same interface.

Do not let UI screens depend directly on Media3, LibVLC, Retrofit, or OkHttp.

---

## 7. Android manifest requirements

Add permissions:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Add location permission only if the app displays or reads the connected SSID:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

Storage rules:

```text
Do not request MANAGE_EXTERNAL_STORAGE.
Do not request legacy external storage.
Do not request READ_EXTERNAL_STORAGE for the dashcam's files.
Use app-specific storage for downloaded clips by default.
Use MediaStore or Storage Access Framework only when the user explicitly exports a clip.
```

Because the dashcam uses cleartext HTTP, set:

```xml
<application
    android:name=".DashcamApp"
    android:usesCleartextTraffic="true"
    ...>
```

Do not communicate with any other cleartext host. The repository must only call `192.168.169.1`.

---

## 8. Network binding and Wi-Fi handling

### 8.1 Core problem

The dashcam Wi-Fi has no internet. Android may keep mobile data or another network as default. The app must force dashcam API and RTSP traffic through the Wi-Fi network connected to the dashcam.

### 8.2 User connection flow

The app must not try to store or change the dashcam Wi-Fi password.

Connection screen behavior:

```text
1. Show current connection status.
2. Tell user to connect phone to DASHCAM Wi-Fi.
3. Provide button: Open Wi-Fi Settings.
4. After returning to app, probe http://192.168.169.1/app/getdeviceattr.
5. If response result == 0, mark device connected.
6. If unreachable, show clear guidance: "Connect to DASHCAM Wi-Fi, then tap Retry."
```

Use:

```kotlin
Intent(Settings.Panel.ACTION_WIFI)
```

when available. Fall back to:

```kotlin
Intent(Settings.ACTION_WIFI_SETTINGS)
```

### 8.3 Network discovery

Implement `DashcamNetworkBinder`:

Responsibilities:

```text
Find a Wi-Fi Network whose LinkProperties include 192.168.169.x or whose route/gateway points to 192.168.169.1.
Verify the candidate network by calling /app/getdeviceattr through that Network.
Bind process to that Network while the app is actively controlling or streaming the dashcam.
Unbind when the app is destroyed or the user disconnects.
```

Implementation details:

```kotlin
connectivityManager.allNetworks
    .filter { network ->
        val caps = connectivityManager.getNetworkCapabilities(network)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
```

For HTTP verification, prefer using the `Network` object:

```kotlin
val url = URL("http://192.168.169.1/app/getdeviceattr")
val connection = network.openConnection(url) as HttpURLConnection
```

For Retrofit/OkHttp calls after verification, either:

```text
Option A: bind process to dashcam network while app is active.
Option B: provide OkHttp socketFactory from network.socketFactory.
```

MVP must use Option A because RTSP player libraries may not accept a custom socket factory. This app has no backend/internet dependency, so binding the process to dashcam Wi-Fi is acceptable.

Required methods:

```kotlin
interface DashcamNetworkBinder {
    val boundNetwork: StateFlow<Network?>
    suspend fun findAndBindDashcamNetwork(): AppResult<Network>
    fun unbind()
}
```

Binding call:

```kotlin
connectivityManager.bindProcessToNetwork(network)
```

Unbinding call:

```kotlin
connectivityManager.bindProcessToNetwork(null)
```

### 8.4 Connection monitor

Implement `DashcamConnectionMonitor`:

```kotlin
interface DashcamConnectionMonitor {
    val connectionState: StateFlow<DashcamConnectionState>
    fun startMonitoring()
    fun stopMonitoring()
    suspend fun probeOnce(): AppResult<DashcamDevice>
}
```

Polling:

```text
When app is foreground: every 2.5 seconds on IO dispatcher.
When app is background: stop polling unless a download worker is running.
When live preview is active: do not spam probes faster than every 5 seconds.
```

Connection states:

```kotlin
sealed interface DashcamConnectionState {
    data object Unknown : DashcamConnectionState
    data object NotConnectedToWifi : DashcamConnectionState
    data object Searching : DashcamConnectionState
    data class Connected(val device: DashcamDevice) : DashcamConnectionState
    data class ApiUnreachable(val message: String) : DashcamConnectionState
    data class WrongWifi(val currentSsid: String?) : DashcamConnectionState
}
```

---

## 9. Domain models

### 9.1 App result and errors

Use typed results:

```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
```

Errors:

```kotlin
sealed interface AppError {
    data object NotConnectedToDashcam : AppError
    data object DashcamApiUnreachable : AppError
    data object UnsupportedEndpoint : AppError
    data object SdCardUnavailable : AppError
    data object RtspUnavailable : AppError
    data object DownloadFailed : AppError
    data object DeleteFailed : AppError
    data object PermissionDenied : AppError
    data object OperationCancelled : AppError
    data class HttpError(val code: Int, val body: String?) : AppError
    data class ApiError(val result: Int, val info: String?) : AppError
    data class ParseError(val raw: String?) : AppError
    data class Unknown(val throwable: Throwable) : AppError
}
```

No ViewModel should catch `Exception` and ignore it. Convert every exception into `AppError.Unknown` and log locally.

### 9.2 Device

```kotlin
data class DashcamDevice(
    val uuid: String,
    val model: String?,
    val softwareVersion: String,
    val hardwareVersion: String,
    val ssid: String,
    val cameraCount: Int,
    val currentCameraId: Int,
    val soc: String?
)
```

### 9.3 Camera side

```kotlin
enum class DashcamCamera(val switchValue: Int, val suffix: String, val displayName: String) {
    FRONT(0, "f", "Front"),
    REAR(1, "b", "Rear")
}
```

### 9.4 Folder

```kotlin
enum class DashcamFolder(val apiValue: String, val displayName: String) {
    LOOP("loop", "Recordings"),
    EVENT("event", "Snapshots"),
    PARK("park", "Parking"),
    EMR("emr", "Emergency"),
    RACE("race", "Race")
}
```

### 9.5 File type

```kotlin
enum class DashcamMediaType {
    VIDEO,
    PICTURE,
    UNKNOWN
}
```

### 9.6 Dashcam file

```kotlin
data class DashcamFile(
    val path: String,
    val filename: String,
    val folder: DashcamFolder,
    val camera: DashcamCamera?,
    val mediaType: DashcamMediaType,
    val sizeKb: Long,
    val cameraLocalDateTime: LocalDateTime?,
    val createTimeRaw: Long?,
    val createTimeString: String?,
    val durationSeconds: Int?,
    val typeRaw: Int,
    val thumbnailUrl: String?
)
```

### 9.7 Paired bundle

```kotlin
data class DashcamFileBundle(
    val id: String,
    val folder: DashcamFolder,
    val mediaType: DashcamMediaType,
    val front: DashcamFile?,
    val rear: DashcamFile?,
    val startTime: LocalDateTime?,
    val totalSizeKb: Long,
    val isCompletePair: Boolean
)
```

`id` must be stable:

```text
folder + yyyyMMddHHmmss rounded/pair-key + mediaType
```

### 9.8 Settings

```kotlin
data class DashcamSettings(
    val micEnabled: Boolean?,
    val osdEnabled: Boolean?,
    val recordingEnabled: Boolean?,
    val loopDuration: LoopDuration?,
    val speakerLevel: LevelSetting?,
    val gSensorSensitivity: LevelSetting?,
    val timelapseRate: TimelapseRate?,
    val resolutionLabel: String?
)

enum class LoopDuration(val value: Int, val label: String) {
    ONE_MIN(0, "1 min"),
    TWO_MIN(1, "2 min"),
    THREE_MIN(2, "3 min")
}

enum class LevelSetting(val value: Int, val label: String) {
    OFF(0, "Off"),
    LOW(1, "Low"),
    MIDDLE(2, "Middle"),
    HIGH(3, "High")
}

enum class TimelapseRate(val value: Int, val label: String) {
    OFF(0, "Off"),
    ONE_SECOND(1, "1 sec"),
    TWO_SECONDS(2, "2 sec"),
    THREE_SECONDS(3, "3 sec")
}
```

### 9.9 Storage

```kotlin
data class StorageStatus(
    val statusRaw: Int,
    val freeMb: Long,
    val totalMb: Long,
    val usedPercent: Float
) {
    val isAvailable: Boolean get() = statusRaw == 0 && totalMb > 0
}
```

---

## 10. File parsing and grouping rules

### 10.1 Filename parser

Implement `DashcamFilenameParser`.

Supported filename pattern:

```regex
(?<date>\d{8})_(?<time>\d{6})_(?<streamId>\d+)_(?<camera>[fb])\.(?<ext>ts|jpg|jpeg|TXT|txt)
```

Examples:

```text
20260713_192150_34_f.ts
20260713_192151_31_b.ts
20260713_173815_34_f.jpg
20260713_173815_31_b.jpg
```

Parsing output:

```kotlin
data class ParsedDashcamFilename(
    val localDateTime: LocalDateTime,
    val streamId: String,
    val camera: DashcamCamera,
    val extension: String
)
```

If parsing fails, keep file visible but mark camera/time unknown.

### 10.2 Pairing algorithm

Implement pairing in `GetFileBundlesUseCase`.

Algorithm:

```text
1. Fetch all file entries for the chosen folder.
2. Map DTOs to DashcamFile.
3. Split by mediaType.
4. For each media type, split into front, rear, and unknown.
5. Sort front and rear by localDateTime descending.
6. For each front file, find nearest rear file whose localDateTime differs by <= 3 seconds.
7. Pair them and mark rear as consumed.
8. Any unmatched rear file becomes its own bundle.
9. Any unknown-camera file becomes its own bundle.
10. Sort bundles descending by startTime, nulls last.
```

Use tolerance because real captures showed front/rear files may differ by one second.

For pictures, use the same pairing algorithm.

### 10.3 Display size

File list `size` is KB-like. Display:

```kotlin
fun Long.kbToDisplayMb(): String = "%.1f MB".format(this / 1024.0)
```

For bundle total size, sum front and rear `sizeKb`.

### 10.4 Time display

Use camera local time from filename or `createtimestr`. Do not convert to UTC. Display:

```text
Today, 19:24
Yesterday, 08:12
13 Jul 2026, 19:24
```

---

## 11. Repositories and use cases

### 11.1 DashcamRepository

```kotlin
interface DashcamRepository {
    suspend fun detectDevice(): AppResult<DashcamDevice>
    suspend fun getProductInfo(): AppResult<ProductInfoDto>
    suspend fun getMediaInfo(): AppResult<MediaInfoDto>
    suspend fun enterRecorder(): AppResult<Unit>
    suspend fun exitPlayback(): AppResult<Unit>
    suspend fun exitSettings(): AppResult<Unit>
    suspend fun getStorageStatus(): AppResult<StorageStatus>
    suspend fun getBatteryInfo(): AppResult<BatteryInfo?>
}
```

### 11.2 FileRepository

```kotlin
interface FileRepository {
    suspend fun getFiles(folder: DashcamFolder, start: Int, end: Int): AppResult<List<DashcamFile>>
    suspend fun getBundles(folder: DashcamFolder): AppResult<List<DashcamFileBundle>>
    suspend fun takeSnapshot(): AppResult<Unit>
    suspend fun deleteFile(path: String): AppResult<Unit>
    fun thumbnailUrl(path: String): String
}
```

Default ranges:

```text
loop: 0..199
event: 0..99
park: 0..99
emr: 0..99
race: 0..99
```

If response count indicates more files exist, add pagination later. MVP can fetch the above ranges.

### 11.3 SettingsRepository

```kotlin
interface SettingsRepository {
    suspend fun getSettings(): AppResult<DashcamSettings>
    suspend fun setMic(enabled: Boolean): AppResult<Unit>
    suspend fun setOsd(enabled: Boolean): AppResult<Unit>
    suspend fun setRecording(enabled: Boolean): AppResult<Unit>
    suspend fun setLoopDuration(duration: LoopDuration): AppResult<Unit>
    suspend fun setSpeaker(level: LevelSetting): AppResult<Unit>
    suspend fun setGSensor(level: LevelSetting): AppResult<Unit>
    suspend fun setTimelapse(rate: TimelapseRate): AppResult<Unit>
    suspend fun switchCamera(camera: DashcamCamera): AppResult<Unit>
}
```

### 11.4 DownloadRepository

```kotlin
interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadItem>>
    suspend fun enqueueFileDownload(file: DashcamFile): AppResult<Unit>
    suspend fun enqueueBundleDownload(bundle: DashcamFileBundle): AppResult<Unit>
    suspend fun cancelDownload(downloadId: String): AppResult<Unit>
    suspend fun deleteLocalDownload(downloadId: String): AppResult<Unit>
}
```

---

## 12. HTTP API implementation

Use Retrofit for structured endpoints and OkHttp for direct streaming downloads.

### 12.1 Retrofit interface

```kotlin
interface DashcamApi {
    @GET("app/getdeviceattr")
    suspend fun getDeviceAttr(): DeviceAttrResponse

    @GET("app/getproductinfo")
    suspend fun getProductInfo(): ProductInfoResponse

    @GET("app/capability")
    suspend fun getCapability(): CapabilityResponse

    @GET("app/getmediainfo")
    suspend fun getMediaInfo(): MediaInfoResponse

    @GET("app/playback")
    suspend fun playback(@Query("param") param: String): StringInfoResponse

    @GET("app/setting")
    suspend fun setting(@Query("param") param: String): StringInfoResponse

    @GET("app/enterrecorder")
    suspend fun enterRecorder(): StringInfoResponse

    @GET("app/getparamitems")
    suspend fun getParamItems(@Query("param") param: String = "all"): ParamItemsResponse

    @GET("app/getparamvalue")
    suspend fun getParamValues(@Query("param") param: String = "all"): ParamValuesResponse

    @GET("app/setparamvalue")
    suspend fun setParamValue(
        @Query("param") param: String,
        @Query("value") value: Int
    ): StringInfoResponse

    @GET("app/getsdinfo")
    suspend fun getSdInfo(): SdInfoResponse

    @GET("app/getrecduration")
    suspend fun getRecDuration(): RecDurationResponse

    @GET("app/getbatteryinfo")
    suspend fun getBatteryInfo(): BatteryInfoResponse

    @GET("app/snapshot")
    suspend fun snapshot(): StringInfoResponse

    @GET("app/getfilelist")
    suspend fun getFileList(
        @Query("folder") folder: String,
        @Query("start") start: Int,
        @Query("end") end: Int
    ): FileListResponse

    @GET("app/deletefile")
    suspend fun deleteFile(@Query("file", encoded = false) fullPath: String): StringInfoResponse
}
```

For `getthumbnail` and direct downloads, prefer building `HttpUrl` manually to avoid encoding mistakes.

### 12.2 API call wrapper

All repository calls must use a shared safe wrapper:

```kotlin
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T>
```

Behavior:

```text
IOException -> DashcamApiUnreachable or DownloadFailed depending context
SocketTimeoutException -> DashcamApiUnreachable
HttpException -> HttpError
SerializationException -> ParseError
result 98 -> UnsupportedEndpoint
result nonzero -> ApiError
CancellationException -> rethrow
other Throwable -> Unknown
```

Never swallow `CancellationException`.

### 12.3 OkHttp client

Configure:

```text
connectTimeout: 5 seconds
readTimeout: 15 seconds for API
writeTimeout: 15 seconds
retryOnConnectionFailure: true
```

For downloads use a separate client:

```text
connectTimeout: 8 seconds
readTimeout: 30 seconds
writeTimeout: 30 seconds
retryOnConnectionFailure: true
```

OkHttp logging:

```text
Debug builds: BASIC logging, never BODY for video downloads.
Release builds: no logging.
```

---

## 13. Live preview implementation

### 13.1 Required behavior

Live preview must:

```text
Use rtsp://192.168.169.1:554/track2
Force TCP transport
Disable audio
Call exit playback mode before preview
Call exit settings mode before preview
Call enter recorder immediately before preview
Start within 5 seconds or show recoverable error
Release player when leaving screen
Recreate player after switching front/rear camera
Prevent multiple active RTSP sessions
```

### 13.2 Live preview startup sequence

`PrepareLivePreviewUseCase` must run:

```text
1. DashcamNetworkBinder.findAndBindDashcamNetwork()
2. DashcamRepository.exitPlayback()
3. DashcamRepository.exitSettings()
4. DashcamRepository.enterRecorder()
5. DashcamRepository.getMediaInfo() for diagnostics only
6. Start LivePreviewEngine with rtsp://192.168.169.1:554/track2
```

If any HTTP step fails, do not start RTSP. Show error with retry.

### 13.3 LivePreviewEngine interface

```kotlin
interface LivePreviewEngine {
    val state: StateFlow<LivePreviewState>
    fun attach(surfaceHost: Any)
    suspend fun start(url: String): AppResult<Unit>
    suspend fun switchCamera(camera: DashcamCamera): AppResult<Unit>
    suspend fun stop()
    fun release()
}
```

`surfaceHost` can be a `PlayerView`, `SurfaceView`, or wrapper depending on the implementation. The UI must not know the concrete player library.

State:

```kotlin
sealed interface LivePreviewState {
    data object Idle : LivePreviewState
    data object Preparing : LivePreviewState
    data object Connecting : LivePreviewState
    data object Playing : LivePreviewState
    data object Buffering : LivePreviewState
    data class Error(val error: AppError) : LivePreviewState
    data object Released : LivePreviewState
}
```

### 13.4 Media3 RTSP implementation

Use Media3 first:

```kotlin
val trackSelector = DefaultTrackSelector(context).apply {
    parameters = buildUponParameters()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
        .build()
}

val player = ExoPlayer.Builder(context)
    .setTrackSelector(trackSelector)
    .build()

val mediaSource = RtspMediaSource.Factory()
    .setForceUseRtpTcp(true)
    .createMediaSource(MediaItem.fromUri(DashcamConstants.RTSP_TRACK2_URL))
```

If Media3 repeatedly fails because of the malformed AAC stream, keep the interface and replace the implementation with LibVLC. Do not alter ViewModels or screens.

### 13.5 Player lifecycle

Rules:

```text
Create player when LiveScreen enters composition or when user taps Start Preview.
Pause/stop when app goes background.
Release on ViewModel.onCleared and DisposableEffect.onDispose.
Never hold Activity reference in ViewModel.
Use applicationContext for player creation where possible.
```

Compose screen must use `DisposableEffect` to release view/player attachments.

---

## 14. Download implementation

### 14.1 Storage location

Default private storage:

```text
context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)/Dashcam/
```

File organization:

```text
Dashcam/
├── Recordings/
│   ├── 20260713_192150_front.ts
│   └── 20260713_192151_rear.ts
├── Snapshots/
│   ├── 20260713_173815_front.jpg
│   └── 20260713_173815_rear.jpg
└── Bundles/
    └── 20260713_192150/
        ├── front.ts
        ├── rear.ts
        └── metadata.json
```

Downloaded filenames must be sanitized. Do not use raw `/mnt/sdcard/...` as local path.

### 14.2 Download worker

Use WorkManager for downloads.

Each download must:

```text
Run on Dispatchers.IO
Use foreground notification for long transfers
Support cancellation
Write to .partial file first
Rename to final file only after success
Resume from .partial if possible
Persist state in Room
Never hold a UI reference
```

### 14.3 Download states

```kotlin
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

`DownloadEntity`:

```kotlin
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val remotePath: String,
    val localPath: String,
    val folder: String,
    val camera: String?,
    val mediaType: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String?
)
```

### 14.4 Download UI

Downloads screen must show:

```text
Active downloads with progress
Completed downloads
Failed downloads with Retry
Open local file
Delete local copy
```

Do not show Android file paths to the user unless in debug diagnostics.

---

## 15. Local database

Use Room.

Database entities:

```text
DownloadEntity
DeviceEntity
```

Optional future entities:

```text
CachedFileListEntity
CachedThumbnailEntity
```

MVP does not need to cache every dashcam file in Room; it can fetch file lists live. Cache downloaded files and last connected device only.

`DeviceEntity`:

```kotlin
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val uuid: String,
    val model: String?,
    val softwareVersion: String,
    val hardwareVersion: String,
    val ssid: String,
    val cameraCount: Int,
    val soc: String?,
    val lastSeenAt: Long
)
```

---

## 16. Preferences

Use DataStore Preferences.

Preferences:

```text
preferred_start_screen: home/live/library
preferred_camera: front/rear
theme_mode: system/light/dark
dynamic_color_enabled: true/false
haptics_enabled: true/false
auto_start_live_preview: true/false
show_debug_diagnostics: true/false
camera_mapping_swapped: true/false
last_known_device_uuid: string
```

Do not store Wi-Fi password.

---

## 17. UI design direction

### 17.1 Visual reference

The attached reference image shows a premium dark mobile UI with:

```text
Charcoal/dark canvas
Large rounded cards
Soft contrast
Layered/overlapping panels
Lime green accent
White/cream surfaces for contrast
Floating controls
Large typographic hierarchy
Bottom navigation with simple icons
```

Adapt this style to dashcam use. Do not copy the home-services content or imagery. Use the aesthetic only.

### 17.2 Theme identity

Create a premium automotive camera theme:

Dark theme:

```text
Background: near-black graphite
Surface: deep charcoal
Surface variant: soft dark grey
Primary accent: electric lime / camera green
Secondary accent: warm amber for recording/snapshot highlights
Danger: soft red for delete/connection failure
Text: warm white, soft grey secondary
```

Light theme:

```text
Background: warm off-white
Surface: white
Surface variant: light grey
Primary accent: deep green/lime
Secondary accent: amber
Danger: red
Text: deep graphite
```

Use Material color roles, not hard-coded colors in screens.

### 17.3 Material 3 Expressive behavior

Use:

```text
Large rounded shapes
Expressive card scaling on press
Smooth AnimatedContent transitions
Container transform style navigation where practical
animateContentSize for expanding cards
Subtle blur/scrim overlays only if performant
Floating action buttons for primary actions
Expressive loading states
Segmented buttons for camera/front-rear and folder tabs
```

Do not over-animate lists. Keep 60 fps.

### 17.4 Shape system

```text
Extra small: 8dp
Small: 12dp
Medium: 18dp
Large: 26dp
Extra large: 32dp
Hero cards: 36dp
Bottom sheets/dialogs: 28dp
```

### 17.5 Spacing system

```text
Screen horizontal padding: 20dp
Compact screen horizontal padding: 16dp
Card internal padding: 16dp
Hero card padding: 20dp
Section spacing: 24dp
Element spacing: 12dp
Tiny spacing: 6dp
```

### 17.6 Typography

Use Material typography roles:

```text
Display/Headline: screen titles and live status
TitleLarge: card titles
TitleMedium: section headers
BodyMedium: metadata
LabelLarge: buttons and chips
LabelSmall: badges
```

Avoid dense small text. The app will often be used near a car; quick readability matters.

---

## 18. Navigation

Use Navigation Compose.

Routes:

```kotlin
sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Live : Route("live")
    data object Library : Route("library")
    data object Downloads : Route("downloads")
    data object Settings : Route("settings")
    data object ClipDetail : Route("clip_detail/{bundleId}")
}
```

Bottom navigation items:

```text
Home
Live
Library
Downloads
Settings
```

On small screens use NavigationBar. On tablets use NavigationRail with two-pane Library/Detail layout.

---

## 19. Screens

### 19.1 Home screen

Purpose: premium dashboard and quick actions.

Content:

```text
Top greeting/title: "Dashcam"
Connection hero card
Device info card
Storage usage card
Quick action row:
  - Live View
  - Snapshot
  - Record On/Off
  - Library
Recent recordings carousel
```

Connection hero states:

```text
Connected: green pulse dot, device model, firmware, camera count
Searching: animated radar/pulse
Not connected: clear instruction to connect to DASHCAM Wi-Fi
Wrong Wi-Fi: show current SSID if permission allows
API unreachable: retry button
```

Quick action behavior:

```text
Live View -> navigate Live
Snapshot -> call snapshot, refresh event count, haptic success
Record On/Off -> confirmation if stopping recording
Library -> navigate Library
```

### 19.2 Live screen

Purpose: live RTSP preview and camera controls.

Layout:

```text
Top app bar: Live View + connection status pill
Hero preview card with 16:9-ish container
Overlay: red/green recording status pill
Overlay: current camera Front/Rear
Controls below:
  - Front / Rear segmented control
  - Snapshot button
  - Record toggle
  - Reconnect stream button
  - Go to Library button
Storage mini-card
```

Preview states:

```text
Idle: show Start Preview button
Preparing: show animated camera lens/loading
Connecting: show "Opening live stream..."
Playing: show video
Buffering: show small non-blocking spinner overlay
Error: show friendly error and Retry
```

Camera switch:

```text
1. Haptic tick.
2. Stop preview engine.
3. Call setparamvalue switchcam.
4. Wait 300 ms.
5. Call enterrecorder.
6. Recreate RTSP session.
```

Snapshot:

```text
1. Call /app/snapshot.
2. On success, show brief check animation and haptic confirm.
3. Do not navigate away automatically.
```

Stop recording:

```text
If user toggles recording off, show confirmation:
"Stop recording? The dashcam will stop saving new footage until recording is turned back on."
Buttons: Cancel / Stop Recording
```

### 19.3 Library screen

Purpose: browse dashcam files.

Tabs/folders:

```text
Recordings = loop
Snapshots = event
Parking = park
Emergency = emr
Race = race
```

Content:

```text
Folder segmented control
Refresh button
Grid/list adaptive layout
Each card shows:
  - thumbnail from front if available, otherwise rear
  - folder/type badge
  - Front/Rear availability badges
  - timestamp
  - total size
  - download status if downloaded/downloading
```

Card interactions:

```text
Tap -> Clip Detail
Long press -> selection mode
Selection mode actions:
  - Download selected
  - Delete selected, with confirmation
```

Empty states:

```text
No recordings: "No recordings found on the dashcam."
No snapshots: "No snapshots yet. Take one from Live View."
Parking/EMR/Race empty: "No files in this folder."
Disconnected: "Connect to DASHCAM Wi-Fi to browse files."
```

### 19.4 Clip detail screen

Purpose: show one front/rear bundle.

Content:

```text
Large thumbnail/preview area
Timestamp
Folder
Front file card if available
Rear file card if available
Total size
Actions:
  - Download front
  - Download rear
  - Download both
  - Delete front
  - Delete rear
  - Delete both
```

If local file downloaded, allow local playback using Media3.

Delete confirmation:

```text
Title: Delete recording?
Message: "This removes the selected file from the dashcam SD card. This cannot be undone."
Require explicit Delete button.
Use danger color.
```

### 19.5 Downloads screen

Purpose: manage local downloads.

Content:

```text
Active downloads
Completed downloads
Failed downloads
Storage location note
```

Actions:

```text
Open
Share/export
Retry
Cancel
Delete local copy
```

Export must use Android Sharesheet or MediaStore/SAF. Do not require broad storage.

### 19.6 Settings screen

Purpose: safe dashcam settings and app preferences.

Sections:

```text
Connection
Recording
Sound and overlay
Sensitivity
Storage
Appearance
Diagnostics
Deferred / not implemented features
```

Dashcam settings controls:

```text
Mic: switch
OSD: switch
Recording: switch with confirmation for off
Loop duration: segmented buttons 1 min / 2 min / 3 min
Speaker: Off / Low / Middle / High
G-sensor: Off / Low / Middle / High
Timelapse: Off / 1s / 2s / 3s
```

App preferences:

```text
Theme: System / Light / Dark
Dynamic color: on/off
Haptics: on/off
Auto-start live preview: on/off
Debug diagnostics: on/off
Swap front/rear labels: on/off
```

Diagnostics card:

```text
Device UUID
Model
Software version
Hardware version
SOC
Camera count
SD total/free
Last API result
```

Deferred features section:

```text
The following features are intentionally not implemented yet:
- Lock/protect video
- Unlock video
- Format SD card
- Change Wi-Fi password
- Change Wi-Fi SSID
```

Do not make these clickable in MVP.

---

## 20. Haptics

Implement `HapticFeedbackManager`.

Respect `haptics_enabled` preference.

Haptic events:

```text
Light tick:
  - tab change
  - camera switch
  - segmented setting choice
  - refresh pull

Confirmation:
  - snapshot success
  - download queued
  - setting saved

Warning:
  - delete dialog opened
  - stop recording dialog opened

Error:
  - connection failed
  - delete failed
  - stream failed after retry
```

Compose implementation:

```kotlin
LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)
```

For stronger effects on supported devices, use `VibratorManager` with predefined effects, but do not require it.

Never vibrate repeatedly in a loop.

---

## 21. Animations

Use animations intentionally.

Required animations:

```text
Connection pulse when connected/searching
Live preview loading lens/ring animation
Card press scale 0.98 -> 1.0
AnimatedContent for connection states
animateContentSize for expanding settings cards
Lazy list item fade/slide on first load
Snapshot success checkmark burst
Download progress smooth animation
```

Performance rules:

```text
No infinite heavy animations while video is playing, except a tiny recording dot.
No large blur over live video.
No animated bitmap decoding.
No recomposition-heavy timer in each list item.
Use stable models and keys in LazyColumn/LazyVerticalGrid.
```

---

## 22. Image and thumbnail loading

Use Coil.

Thumbnail URL:

```text
http://192.168.169.1/app/getthumbnail?file=<path>
```

Coil rules:

```text
Use crossfade, but keep it short.
Set size constraints.
Use memory cache for visible thumbnails.
Do not prefetch hundreds of thumbnails at once.
Lazy grid should load thumbnails only for visible items.
Show placeholder gradient card.
Show broken thumbnail icon on failure.
```

For local downloaded pictures, load from local URI/file.

---

## 23. Error handling UX

Every failure shown to user must be actionable.

Mappings:

```text
NotConnectedToDashcam:
"Connect to DASHCAM Wi-Fi, then try again."
Action: Open Wi-Fi Settings, Retry

DashcamApiUnreachable:
"The app could not reach the dashcam. Make sure the camera is powered on and your phone is connected to DASHCAM Wi-Fi."
Action: Retry

UnsupportedEndpoint:
"This feature is not supported by this dashcam."
Action: Dismiss

RtspUnavailable:
"Live preview could not start. Re-entering recorder mode may fix it."
Action: Retry Live Preview

DownloadFailed:
"Download failed. Keep your phone connected to DASHCAM Wi-Fi and try again."
Action: Retry

DeleteFailed:
"Delete failed. The file may already be gone or the dashcam is busy."
Action: Refresh
```

Do not show raw stack traces outside debug diagnostics.

---

## 24. Threading and ANR prevention

Rules:

```text
Network calls: Dispatchers.IO
File IO: Dispatchers.IO
Room: suspend DAO / Flow
JSON parsing: IO if large
Thumbnail decoding: Coil-managed
Player operations: main-safe wrapper, heavy work off main where library allows
```

ViewModels:

```text
Use viewModelScope.
Use SupervisorJob behavior for independent tasks.
Never call runBlocking.
Never use Thread.sleep.
Use delay only in coroutines.
Use MutableStateFlow private, expose StateFlow public.
```

Compose:

```text
Collect StateFlow with collectAsStateWithLifecycle.
Use remember for stable objects.
Use LaunchedEffect with stable keys.
Use DisposableEffect for player view cleanup.
Do not perform network calls directly in composables.
```

---

## 25. Memory and leak prevention

Rules:

```text
No Activity references in singleton/repository/player classes.
Use applicationContext for long-lived services.
Release ExoPlayer/LibVLC on stop/release.
Cancel thumbnail/download jobs when screen leaves if not needed.
Do not load full 4K video into memory.
Stream downloads to disk using byte buffers.
Use 64 KB to 256 KB buffers for downloads.
Avoid storing large bitmaps in ViewModel state.
Store URLs/paths, not bitmap objects.
```

Add debug-only StrictMode in `DashcamApp`:

```text
Detect disk/network on main thread in debug.
Log violations.
Do not crash release builds.
```

---

## 26. Recording controls

Recording status is controlled by:

```http
GET /app/getparamvalue?param=rec
GET /app/setparamvalue?param=rec&value=0
GET /app/setparamvalue?param=rec&value=1
```

UI rule:

```text
Starting recording: immediate action after tap.
Stopping recording: confirmation dialog required.
```

After changing recording state:

```text
1. Wait 300 ms.
2. Refresh getparamvalue?param=rec.
3. Update UI from actual camera state, not optimistic state only.
```

---

## 27. Safe settings update flow

For any setting change:

```text
1. Ensure connected/bound.
2. Call /app/setting?param=enter if not already in settings mode.
3. Call /app/setparamvalue?param=X&value=Y.
4. If result 0, call /app/getparamvalue?param=all.
5. Update UI from confirmed values.
6. Call /app/setting?param=exit when leaving Settings screen.
```

If live preview is active and user changes settings, stop preview first unless setting is known to be safe. MVP rule: Settings screen and Live preview should not be active at the same time.

---

## 28. Snapshot flow

Snapshot command:

```http
GET /app/snapshot
```

After success:

```text
1. Haptic confirmation.
2. Show snackbar: "Snapshot saved on dashcam."
3. Refresh event file list if Library is already loaded.
4. Do not automatically download the snapshot.
```

If snapshot fails:

```text
Show: "Snapshot failed. Check dashcam connection and try again."
```

---

## 29. Delete flow

Delete command:

```http
GET /app/deletefile?file=<FULL_FILE_PATH>
```

Rules:

```text
Confirmation required.
Show filename/camera side in dialog.
For delete both, perform sequential deletes.
Do not perform parallel deletes against the dashcam.
If any delete succeeds, refresh folder list.
If all fail, keep UI unchanged and show error.
```

Sequential delete pseudo-code:

```kotlin
for (file in filesToDelete) {
    when (val result = fileRepository.deleteFile(file.path)) {
        is AppResult.Success -> markDeleted(file)
        is AppResult.Failure -> markFailed(file, result.error)
    }
    delay(150)
}
refreshFolder()
```

---

## 30. Diagnostics mode

Diagnostics are hidden unless user enables `show_debug_diagnostics`.

Diagnostics screen/card must show:

```text
Current bound network status
Last device probe response
Last API error
RTSP URL
HTTP base URL
Device UUID/model/software/hardware
Storage total/free
Current settings raw values
```

Add button:

```text
Copy diagnostics
```

Copied text must not include personal phone identifiers.

---

## 31. Testing requirements

### 31.1 Unit tests

Required unit tests:

```text
DashcamFilenameParser parses front video filename
DashcamFilenameParser parses rear video filename
DashcamFilenameParser parses picture filename
DashcamFilenameParser handles invalid filename
File pairing pairs front/rear within 3 seconds
File pairing does not pair files beyond 3 seconds
File pairing keeps unmatched front file
File pairing keeps unmatched rear file
sizeKb display conversion
setting value mapping
result 98 maps to UnsupportedEndpoint
nonzero result maps to ApiError
```

### 31.2 Fake API tests

Use MockWebServer for:

```text
getdeviceattr success
getdeviceattr timeout
getfilelist loop success
getfilelist empty folder
setparamvalue success
setparamvalue unsupported result 98
delete success
delete failure
```

### 31.3 ViewModel tests

Required:

```text
HomeViewModel shows connected state after detectDevice success
HomeViewModel shows retry state after unreachable API
LiveViewModel runs prepare sequence before starting player
LiveViewModel stops player on clear
LibraryViewModel groups files correctly
SettingsViewModel refreshes after setting update
```

### 31.4 Manual QA checklist

```text
Install app fresh.
Open app while not connected to DASHCAM Wi-Fi.
Open Wi-Fi settings from app.
Connect to DASHCAM.
Return to app and verify connected state.
Open Live Preview.
Switch front/rear.
Take snapshot.
Open Library.
See recordings and snapshots.
Open clip detail.
Download front clip.
Download front+rear bundle.
Delete a test snapshot after confirmation.
Change loop duration.
Toggle mic.
Toggle OSD.
Toggle theme light/dark/system.
Disconnect Wi-Fi and confirm graceful error.
Reconnect and confirm recovery.
Rotate phone during live preview.
Put app background and return.
Confirm no crash, no leaked player audio/video, no stuck notification.
```

---

## 32. Acceptance criteria

The MVP is complete only when all criteria pass.

### 32.1 Functional

```text
App detects dashcam at 192.168.169.1.
App shows connected device details.
App can show live preview from rtsp://192.168.169.1:554/track2.
App can switch front/rear preview using switchcam 0/1.
App can list loop recordings.
App can list event snapshots.
App can display thumbnails.
App can group front/rear pairs.
App can download single front/rear file.
App can download front+rear bundle.
App can take snapshot.
App can delete selected file with confirmation.
App can read storage status.
App can read settings.
App can update mic, OSD, speaker, G-sensor, loop duration, timelapse, recording state.
App works without internet.
App contains no login, backend, Firebase, analytics, ads, or cloud feature.
```

### 32.2 Quality

```text
No network or file IO on main thread.
No ANR during file browsing or download.
No OutOfMemoryError from thumbnails.
No crash when dashcam disconnects mid-operation.
No crash when RTSP stream refuses connection.
No player leak after navigating away.
No duplicate download workers for same remote file.
No dangerous feature exposed accidentally.
Dark and light themes both polished.
Haptics can be disabled.
```

---

## 33. Implementation milestones

### Milestone 1: Project foundation

```text
Create project/package.
Set up Compose Material 3 theme.
Set up Hilt.
Set up Retrofit/OkHttp.
Set up Room/DataStore.
Set up navigation shell.
Create base screens with fake data.
```

### Milestone 2: Connection and device detection

```text
Implement DashcamNetworkBinder.
Implement getdeviceattr and getproductinfo.
Implement Home connected/disconnected states.
Add Wi-Fi settings button.
```

### Milestone 3: Live preview

```text
Implement prepare live sequence.
Implement RTSP live preview engine.
Implement front/rear switching.
Implement snapshot button.
Implement record toggle.
```

### Milestone 4: Library

```text
Implement file list endpoints.
Implement filename parser.
Implement front/rear grouping.
Implement thumbnails.
Implement Library and Clip Detail screens.
```

### Milestone 5: Downloads

```text
Implement WorkManager downloads.
Implement .partial resume behavior.
Implement Downloads screen.
Implement local playback/share for downloaded files.
```

### Milestone 6: Settings

```text
Implement get settings.
Implement safe setting updates.
Implement storage card.
Implement appearance/haptics preferences.
```

### Milestone 7: Hardening

```text
Add tests.
Add diagnostics mode.
Add StrictMode debug.
Test Wi-Fi disconnects.
Test stream reconnects.
Test memory behavior with large lists.
Polish animations/haptics.
```

---

## 34. Future TODOs, deliberately deferred

These features must not be implemented in MVP. Keep them documented only.

```text
Lock/protect video
Unlock video
Format SD card
Change Wi-Fi password
Change Wi-Fi SSID
GPS route parsing if compatible GPS files are later confirmed
Firmware update or firmware information beyond read-only display
Automatic Wi-Fi password handling
Cloud backup
Remote live view
Push notifications
```

Implementation condition for deferred dashcam-control features:

```text
Only implement after capturing the exact Viidure command safely with PCAPdroid or equivalent.
Never brute-force destructive endpoints.
Never guess format or Wi-Fi-change URLs.
```

---

## 35. Codex implementation rules

Codex must follow these rules strictly:

```text
Do not add Firebase.
Do not add login.
Do not add a backend.
Do not add analytics.
Do not add ads.
Do not add cloud sync.
Do not add unrequested permissions.
Do not request MANAGE_EXTERNAL_STORAGE.
Do not use GlobalScope.
Do not block the main thread.
Do not expose format SD or Wi-Fi password/SSID changes.
Do not implement lock/unlock yet.
Do not hard-code endpoint strings outside the API/constants layer.
Do not let composables perform network calls directly.
Do not pass Activity context into singletons.
Do not keep player instances alive after leaving Live screen.
Do not load full video or large bitmap files into memory.
Do not crash on result 98; map it to UnsupportedEndpoint.
Do not show raw stack traces to normal users.
```

If a requirement conflicts with existing generated code, change the code to match this specification.

---

## 36. MVP summary

The first complete build must deliver:

```text
A beautiful private Material 3 Expressive dashcam app that connects directly to the YantopCam Wi-Fi, detects the camera, shows live preview, switches front/rear view, takes snapshots, browses recordings, groups front/rear files, shows thumbnails, downloads clips, deletes selected files with confirmation, displays storage, and manages safe settings — all locally, with no backend, no login, no Firebase, and robust handling of connection loss and stream failures.
```
