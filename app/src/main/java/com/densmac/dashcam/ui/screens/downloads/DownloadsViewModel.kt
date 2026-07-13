package com.densmac.dashcam.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.dashcam.core.common.AppNotice
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadRepository.observeDownloads().collectLatest { downloads ->
                _uiState.update { it.copy(downloads = downloads) }
            }
        }
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
