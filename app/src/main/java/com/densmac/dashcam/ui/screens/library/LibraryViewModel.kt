package com.densmac.dashcam.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.dashcam.core.common.AppNotice
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.deleteFilesUserMessage
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
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
class LibraryViewModel @Inject constructor(
    private val getFileBundles: GetFileBundlesUseCase,
    private val deleteFile: DeleteFileUseCase,
    private val downloadRepository: DownloadRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        load(DashcamFolder.LOOP)
    }

    fun load(folder: DashcamFolder = uiState.value.folder) {
        viewModelScope.launch {
            _uiState.update { it.copy(folder = folder, loading = true, message = null, selectedIds = emptySet()) }
            when (val result = getFileBundles(folder)) {
                is AppResult.Success -> _uiState.update { it.copy(loading = false, bundles = result.data) }
                is AppResult.Failure -> _uiState.update { it.copy(loading = false, message = result.error.userMessage()) }
            }
        }
    }

    fun toggleSelection(bundle: DashcamFileBundle) {
        _uiState.update { state ->
            val next = if (bundle.id in state.selectedIds) state.selectedIds - bundle.id else state.selectedIds + bundle.id
            state.copy(selectedIds = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun requestDelete(files: List<DashcamFile>) {
        _uiState.update { it.copy(pendingDelete = files) }
    }

    fun requestDeleteSelected() {
        val state = uiState.value
        val files = state.bundles.filter { it.id in state.selectedIds }.flatMap { listOfNotNull(it.front, it.rear) }
        requestDelete(files)
    }

    fun dismissDelete() {
        _uiState.update { it.copy(pendingDelete = emptyList()) }
    }

    fun confirmDelete() {
        val files = uiState.value.pendingDelete
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, pendingDelete = emptyList()) }
            val result = deleteFile(files)
            val message = deleteFilesUserMessage(result.deleted.size, result.failed.size)
            _uiState.update { it.copy(message = message, selectedIds = emptySet()) }
            load()
        }
    }

    fun downloadBundle(bundle: DashcamFileBundle) {
        viewModelScope.launch {
            when (val result = downloadRepository.enqueueBundleDownload(bundle)) {
                is AppResult.Success -> _uiState.update { it.copy(message = AppNotice.DownloadQueued.userMessage()) }
                is AppResult.Failure -> _uiState.update { it.copy(message = result.error.userMessage()) }
            }
        }
    }
}
