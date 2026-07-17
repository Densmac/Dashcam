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
    preferencesDataSource: com.densmac.dashcam.data.datastore.UserPreferencesDataSource,
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
            preferencesDataSource.preferences.collectLatest { prefs ->
                _uiState.update { it.copy(externalPlayerPackage = prefs.externalPlayerPackage) }
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

    /** Swipe to the next completed download in the list and play it. */
    fun playNext() = navigatePlaying(1)

    /** Swipe to the previous completed download in the list and play it. */
    fun playPrevious() = navigatePlaying(-1)

    private fun navigatePlaying(delta: Int) {
        val current = uiState.value.playing ?: return
        val completed = uiState.value.downloads.filter { it.status == DownloadStatus.COMPLETED }
        val index = completed.indexOfFirst { it.id == current.id }
        if (index < 0) return
        val target = completed.getOrNull(index + delta) ?: return
        play(target)
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

    /** Replay from the start (used after the clip ends). */
    fun replay() {
        viewModelScope.launch { mediaPlayer.seekTo(0f) }
    }

    fun beginScrub() = mediaPlayer.beginScrub()
    fun previewScrub(fraction: Float) = mediaPlayer.previewScrub(fraction)
    fun seekTo(fraction: Float) {
        viewModelScope.launch { mediaPlayer.seekTo(fraction) }
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
