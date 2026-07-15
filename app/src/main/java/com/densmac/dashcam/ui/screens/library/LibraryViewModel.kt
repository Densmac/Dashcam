package com.densmac.dashcam.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.dashcam.core.common.AppNotice
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.deleteFilesUserMessage
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.core.network.DashcamConnectionMonitor
import com.densmac.dashcam.domain.model.DashcamConnectionState
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DashcamFolder
import com.densmac.dashcam.domain.repository.DownloadRepository
import com.densmac.dashcam.domain.repository.FileRepository
import com.densmac.dashcam.domain.usecase.DeleteFileUseCase
import com.densmac.dashcam.domain.usecase.GetFileBundlesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getFileBundles: GetFileBundlesUseCase,
    private val deleteFile: DeleteFileUseCase,
    private val downloadRepository: DownloadRepository,
    private val fileRepository: FileRepository,
    private val connectionMonitor: DashcamConnectionMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private var loadedForDeviceUuid: String? = null
    private var wasConnected = false
    private val thumbnailMutex = Mutex()
    private val thumbnailCache = LinkedHashMap<String, ByteArray>()
    private val thumbnailRequests = mutableMapOf<String, Deferred<ByteArray?>>()

    init {
        connectionMonitor.startMonitoring()
        viewModelScope.launch {
            connectionMonitor.connectionState.collect { state ->
                if (state is DashcamConnectionState.Connected) {
                    val shouldReload = !wasConnected || loadedForDeviceUuid != state.device.uuid
                    wasConnected = true
                    if (shouldReload) {
                        loadedForDeviceUuid = state.device.uuid
                        load(uiState.value.folder)
                    }
                } else {
                    wasConnected = false
                }
            }
        }
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

    suspend fun loadThumbnail(file: DashcamFile): ByteArray? {
        val path = file.path
        thumbnailMutex.withLock {
            thumbnailCache[path]?.let { return it }
        }

        val request = thumbnailMutex.withLock {
            thumbnailCache[path]?.let { return it }
            thumbnailRequests[path] ?: viewModelScope.async {
                fetchThumbnail(file)
            }.also { thumbnailRequests[path] = it }
        }

        return try {
            request.await()
        } finally {
            thumbnailMutex.withLock {
                if (thumbnailRequests[path] === request) thumbnailRequests.remove(path)
            }
        }
    }

    private suspend fun fetchThumbnail(file: DashcamFile): ByteArray? {
        val path = file.path
        val bytes = when (val result = fileRepository.getThumbnailBytes(path)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> null
        }
        val cached = thumbnailMutex.withLock { thumbnailCache[path] }
        if (bytes == null || bytes.size < MIN_USEFUL_THUMBNAIL_BYTES) return cached

        thumbnailMutex.withLock {
            thumbnailCache[path] = bytes
            while (thumbnailCache.size > THUMBNAIL_CACHE_LIMIT) {
                val eldest = thumbnailCache.entries.iterator().next()
                thumbnailCache.remove(eldest.key)
            }
        }
        return bytes
    }

    override fun onCleared() {
        connectionMonitor.stopMonitoring()
        super.onCleared()
    }

    private companion object {
        const val MIN_USEFUL_THUMBNAIL_BYTES = 4 * 1024
        const val THUMBNAIL_CACHE_LIMIT = 160
    }
}
