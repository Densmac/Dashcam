package com.densmac.dashcam.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.dashcam.core.common.AppNotice
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.core.player.MediaFilePlayerController
import com.densmac.dashcam.core.player.MediaPlaybackState
import com.densmac.dashcam.domain.model.DownloadItem
import com.densmac.dashcam.domain.model.DownloadStatus
import com.densmac.dashcam.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    val mediaPlayer: MediaFilePlayerController
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadRepository.observeDownloads().collectLatest { downloads ->
                _uiState.update { it.copy(downloads = downloads) }
            }
        }
        viewModelScope.launch {
            mediaPlayer.state.collectLatest { state ->
                _uiState.update { it.copy(playbackState = state) }
            }
        }
    }

    /** Open a completed download in the in-app player (local file, no network). */
    fun play(item: DownloadItem) {
        if (item.status != DownloadStatus.COMPLETED) return
        viewModelScope.launch {
            _uiState.update { it.copy(playing = item) }
            mediaPlayer.play("file://${item.localPath}")
        }
    }

    fun closePlayer() {
        mediaPlayer.release()
        _uiState.update { it.copy(playing = null, playbackState = MediaPlaybackState.Idle) }
    }

    fun retryPlayback() {
        viewModelScope.launch { mediaPlayer.retry() }
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

    fun localFileFor(item: DownloadItem): File = File(item.localPath)

    override fun onCleared() {
        mediaPlayer.release()
        super.onCleared()
    }

    fun retry(id: String) = action { downloadRepository.retryDownload(id) }
    fun cancel(id: String) = action { downloadRepository.cancelDownload(id) }
    fun requestDelete(id: String) {
        _uiState.update { it.copy(pendingDeleteId = id) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(pendingDeleteId = null) }
    }

    fun confirmDelete() {
        val id = uiState.value.pendingDeleteId ?: return
        _uiState.update { it.copy(pendingDeleteId = null) }
        action { downloadRepository.deleteLocalDownload(id) }
    }

    private fun action(block: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            when (val result = block()) {
                is AppResult.Success -> _uiState.update { it.copy(message = AppNotice.Updated.userMessage()) }
                is AppResult.Failure -> _uiState.update { it.copy(message = result.error.userMessage()) }
            }
        }
    }
}
