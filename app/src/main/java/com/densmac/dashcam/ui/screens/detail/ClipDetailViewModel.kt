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
    val mediaPlayer: MediaFilePlayerController
) : ViewModel() {
    private val bundleId: String = checkNotNull(savedStateHandle["bundleId"])
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
            val localPath = uiState.value.localPathFor(file)
            if (localPath != null) {
                mediaPlayer.play("file://$localPath")
                return@launch
            }
            // Streaming raw /mnt/sdcard files needs the process bound to the dashcam network and
            // the camera in playback mode (mirrors what the download worker does before a raw GET).
            when (val prepared = dashcamRepository.enterPlayback()) {
                is AppResult.Success -> {
                    val url = DashcamConstants.HTTP_BASE_URL + file.path.removePrefix("/")
                    mediaPlayer.play(url)
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

    override fun onCleared() {
        mediaPlayer.release()
        super.onCleared()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val folder = DashcamFolder.fromApiValue(bundleId.substringBefore('_'))
            when (val result = getFileBundles(folder)) {
                is AppResult.Success -> _uiState.update { it.copy(loading = false, bundle = result.data.firstOrNull { bundle -> bundle.id == bundleId }) }
                is AppResult.Failure -> _uiState.update { it.copy(loading = false, message = result.error.userMessage()) }
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
            _uiState.update { it.copy(message = AppNotice.DownloadQueued.userMessage()) }
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
