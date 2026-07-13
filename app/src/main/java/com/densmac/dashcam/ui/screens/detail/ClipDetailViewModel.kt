package com.densmac.dashcam.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.dashcam.core.common.AppNotice
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFolder
import com.densmac.dashcam.domain.repository.DownloadRepository
import com.densmac.dashcam.domain.usecase.DeleteFileUseCase
import com.densmac.dashcam.domain.usecase.GetFileBundlesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClipDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getFileBundles: GetFileBundlesUseCase,
    private val downloadRepository: DownloadRepository,
    private val deleteFile: DeleteFileUseCase
) : ViewModel() {
    private val bundleId: String = checkNotNull(savedStateHandle["bundleId"])
    private val _uiState = MutableStateFlow(ClipDetailUiState(loading = true))
    val uiState: StateFlow<ClipDetailUiState> = _uiState.asStateFlow()

    init {
        load()
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
