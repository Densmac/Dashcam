package com.densmac.dashcam.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.dashcam.core.common.AppNotice
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.core.player.MediaFilePlayerController
import com.densmac.dashcam.core.player.MediaPlaybackState
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DashcamFolder
import com.densmac.dashcam.domain.model.DownloadStatus
import com.densmac.dashcam.domain.repository.DashcamRepository
import com.densmac.dashcam.domain.repository.DownloadRepository
import com.densmac.dashcam.domain.usecase.DeleteFileUseCase
import com.densmac.dashcam.domain.usecase.GetFileBundlesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClipDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getFileBundles: GetFileBundlesUseCase,
    private val downloadRepository: DownloadRepository,
    private val deleteFile: DeleteFileUseCase,
    private val dashcamRepository: DashcamRepository,
    private val downloadCoordinator: com.densmac.dashcam.data.download.DownloadCoordinator,
    private val streamProxy: com.densmac.dashcam.data.stream.DashcamStreamProxy,
    private val durationProbe: com.densmac.dashcam.data.stream.TsDurationProbe,
    preferencesDataSource: com.densmac.dashcam.data.datastore.UserPreferencesDataSource,
    val mediaPlayer: MediaFilePlayerController
) : ViewModel() {
    private val bundleId: String = checkNotNull(savedStateHandle["bundleId"])
    // Which clip in [bundles] is currently shown. Starts at the clip we were opened on and moves as
    // the user swipes between clips.
    private var currentBundleId: String = bundleId
    private var bundles: List<DashcamFileBundle> = emptyList()
    private val _uiState = MutableStateFlow(ClipDetailUiState(loading = true))
    val uiState: StateFlow<ClipDetailUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            mediaPlayer.state.collect { state ->
                _uiState.update { it.copy(playbackState = state) }
            }
        }
        viewModelScope.launch {
            downloadRepository.observeDownloads().collect { downloads ->
                val completed = downloads
                    .filter { it.status == DownloadStatus.COMPLETED }
                    .associate { it.remotePath to it.localPath }
                _uiState.update { it.copy(downloadedPaths = completed) }
            }
        }
        viewModelScope.launch {
            preferencesDataSource.preferences.collect { prefs ->
                _uiState.update { it.copy(externalPlayerPackage = prefs.externalPlayerPackage) }
            }
        }
    }

    /**
     * Open a dashcam recording in the in-app viewer. If it has already been downloaded, play the
     * local copy (no network needed); otherwise stream it directly from the dashcam over HTTP.
     */
    /** Select a side (front/rear) and start playing it. */
    fun selectSide(camera: com.densmac.dashcam.domain.model.DashcamCamera) {
        val file = when (camera) {
            com.densmac.dashcam.domain.model.DashcamCamera.FRONT -> uiState.value.bundle?.front
            com.densmac.dashcam.domain.model.DashcamCamera.REAR -> uiState.value.bundle?.rear
        }
        _uiState.update { it.copy(selectedSide = camera) }
        file?.let { stream(it) }
    }

    fun stream(file: DashcamFile) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(streamingFile = file, selectedSide = file.camera ?: it.selectedSide, message = null)
            }
            // Seed the scrubber with the clip's known length so seeking is available even when
            // LibVLC can't measure a remote .ts stream.
            val knownDurationMs = (file.durationSeconds?.takeIf { it > 0 } ?: 0) * 1000L
            val localPath = uiState.value.localPathFor(file)
            if (localPath != null) {
                // Local playback doesn't touch the camera, so downloads need not pause.
                downloadCoordinator.setStreaming(false)
                mediaPlayer.play("file://$localPath", knownDurationMs)
                return@launch
            }
            // Streaming raw /mnt/sdcard files needs the process bound to the dashcam network and
            // the camera in playback mode (mirrors what the download worker does before a raw GET).
            when (val prepared = dashcamRepository.enterPlayback()) {
                is AppResult.Success -> {
                    val cameraUrl = DashcamConstants.HTTP_BASE_URL + file.path.removePrefix("/")
                    // Derive the real duration from the TS timestamps before streaming (single-session
                    // camera, so this must happen before the proxy opens its connection). Time-capped
                    // so a slow/failed probe never holds up playback — falls back to the file-list seed.
                    val probed = kotlinx.coroutines.withTimeoutOrNull(DURATION_PROBE_TIMEOUT_MS) {
                        durationProbe.probeDurationMs(cameraUrl)
                    } ?: 0L
                    val effectiveDuration = if (probed > 0L) probed else knownDurationMs
                    // The camera honours Range but never advertises Accept-Ranges, so LibVLC can't
                    // seek it directly. Front it with a loopback proxy that adds the header, making
                    // the stream seekable (and giving us EndReached + position tracking).
                    val playUrl = streamProxy.start(cameraUrl)
                    // Pause downloads while this camera stream plays (single-session camera).
                    downloadCoordinator.setStreaming(true)
                    mediaPlayer.play(playUrl, effectiveDuration)
                }
                is AppResult.Failure ->
                    _uiState.update { it.copy(message = prepared.error.userMessage()) }
            }
        }
    }

    fun retryStream() {
        uiState.value.streamingFile?.let { stream(it) }
    }

    fun stopStream() {
        downloadCoordinator.setStreaming(false)
        streamProxy.stop()
        mediaPlayer.release()
        _uiState.update { it.copy(streamingFile = null, playbackState = MediaPlaybackState.Idle) }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            when (uiState.value.playbackState) {
                MediaPlaybackState.Playing -> mediaPlayer.pause()
                MediaPlaybackState.Paused -> mediaPlayer.resume()
                else -> Unit
            }
        }
    }

    fun beginScrub() = mediaPlayer.beginScrub()
    fun previewScrub(fraction: Float) = mediaPlayer.previewScrub(fraction)
    fun seekTo(fraction: Float) {
        viewModelScope.launch { mediaPlayer.seekTo(fraction) }
    }

    /** Replay from the start (used after the clip ends). */
    fun replay() {
        viewModelScope.launch { mediaPlayer.seekTo(0f) }
    }

    override fun onCleared() {
        downloadCoordinator.setStreaming(false)
        streamProxy.stop()
        mediaPlayer.release()
        super.onCleared()
    }

    private companion object {
        // Settle time after stopping a clip before opening the next one, so the single-session
        // camera can free the previous playback socket.
        const val CLIP_SWITCH_SETTLE_MS = 350L
        // Cap on the pre-stream duration probe so playback isn't held up if it's slow.
        const val DURATION_PROBE_TIMEOUT_MS = 3000L
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val folder = DashcamFolder.fromApiValue(bundleId.substringBefore('_'))
            when (val result = getFileBundles(folder)) {
                is AppResult.Success -> {
                    bundles = result.data
                    _uiState.update { it.copy(loading = false, bundle = bundles.firstOrNull { bundle -> bundle.id == currentBundleId }) }
                }
                is AppResult.Failure -> _uiState.update { it.copy(loading = false, message = result.error.userMessage()) }
            }
        }
    }

    /** Swipe to the next (older) clip in the folder. */
    fun goToNext() = navigateBy(1)

    /** Swipe to the previous (newer) clip in the folder. */
    fun goToPrevious() = navigateBy(-1)

    private fun navigateBy(delta: Int) {
        val index = bundles.indexOfFirst { it.id == currentBundleId }
        if (index < 0) return
        val target = bundles.getOrNull(index + delta) ?: return
        // Carry playback state across the switch: if a clip was open, auto-play the next one on the
        // same side so a swipe feels continuous.
        val wasStreaming = uiState.value.streamingFile != null
        val side = uiState.value.selectedSide
        currentBundleId = target.id
        if (wasStreaming) {
            // Keep the video surface bound (don't release it) and switch the shown clip. Tear down
            // the old stream, let the single-session camera free the playback socket, then open the
            // next clip on the same surface.
            _uiState.update { it.copy(bundle = target, selectedSide = side, message = null) }
            viewModelScope.launch {
                mediaPlayer.stop()
                delay(CLIP_SWITCH_SETTLE_MS)
                selectSide(side)
            }
        } else {
            downloadCoordinator.setStreaming(false)
            streamProxy.stop()
            mediaPlayer.release()
            _uiState.update {
                it.copy(
                    bundle = target,
                    selectedSide = side,
                    streamingFile = null,
                    playbackState = MediaPlaybackState.Idle,
                    message = null
                )
            }
        }
    }

    fun download(files: List<DashcamFile>) {
        viewModelScope.launch {
            files.forEach { file ->
                val result = downloadRepository.enqueueFileDownload(file)
                if (result is AppResult.Failure) {
                    _uiState.update { it.copy(message = result.error.userMessage()) }
                    return@launch
                }
            }
            // Flash a centered confirmation icon instead of a text notice.
            _uiState.update { it.copy(message = null, downloadQueuedTick = it.downloadQueuedTick + 1) }
        }
    }

    fun requestDelete(files: List<DashcamFile>) {
        _uiState.update { it.copy(pendingDelete = files) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(pendingDelete = emptyList()) }
    }

    fun confirmDelete() {
        val files = uiState.value.pendingDelete
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, pendingDelete = emptyList()) }
            val result = deleteFile(files)
            _uiState.update {
                it.copy(
                    loading = false,
                    message = if (result.failed.isEmpty()) {
                        AppNotice.DeletedFromDashcam.userMessage()
                    } else {
                        AppNotice.DeletePartiallyFailed.userMessage()
                    }
                )
            }
            load()
        }
    }
}
