package com.densmac.dashcam.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.dashcam.core.common.AppError
import com.densmac.dashcam.core.common.AppNotice
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.recordingUserMessage
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.core.network.DashcamConnectionMonitor
import com.densmac.dashcam.core.player.LivePreviewEngine
import com.densmac.dashcam.core.player.LivePreviewState
import com.densmac.dashcam.data.datastore.UserPreferences
import com.densmac.dashcam.data.datastore.UserPreferencesDataSource
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamConnectionState
import com.densmac.dashcam.domain.repository.DashcamRepository
import com.densmac.dashcam.domain.repository.SettingsRepository
import com.densmac.dashcam.domain.usecase.PrepareLivePreviewUseCase
import com.densmac.dashcam.domain.usecase.StartStopRecordingUseCase
import com.densmac.dashcam.domain.usecase.SwitchCameraUseCase
import com.densmac.dashcam.domain.usecase.TakeSnapshotUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val connectionMonitor: DashcamConnectionMonitor,
    private val dashcamRepository: DashcamRepository,
    private val settingsRepository: SettingsRepository,
    private val prepareLivePreview: PrepareLivePreviewUseCase,
    private val switchCameraUseCase: SwitchCameraUseCase,
    private val takeSnapshot: TakeSnapshotUseCase,
    private val recordingUseCase: StartStopRecordingUseCase,
    private val preferencesDataSource: UserPreferencesDataSource,
    val livePreviewEngine: LivePreviewEngine
) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()
    private var loadedStatusForDeviceUuid: String? = null
    private var autoStartedForDeviceUuid: String? = null
    private var wasConnected = false
    private var preferences = UserPreferences()
    private val previewCommandMutex = Mutex()

    init {
        connectionMonitor.startMonitoring()
        viewModelScope.launch {
            connectionMonitor.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is DashcamConnectionState.Connected) {
                    val shouldRefresh = !wasConnected || loadedStatusForDeviceUuid != state.device.uuid
                    wasConnected = true
                    if (shouldRefresh) {
                        loadedStatusForDeviceUuid = state.device.uuid
                        loadDashcamStatus()
                        maybeAutoStartPreview(state.device.uuid)
                    }
                } else {
                    wasConnected = false
                    autoStartedForDeviceUuid = null
                }
            }
        }
        viewModelScope.launch {
            preferencesDataSource.preferences.collectLatest { prefs ->
                preferences = prefs
                _uiState.update { it.copy(selectedCamera = prefs.preferredCamera) }
            }
        }
        viewModelScope.launch {
            livePreviewEngine.state.collectLatest { state ->
                _uiState.update { it.copy(previewState = state) }
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            when (val probe = connectionMonitor.probeOnce()) {
                is AppResult.Success -> {
                    loadedStatusForDeviceUuid = probe.data.uuid
                    loadDashcamStatus()
                    maybeAutoStartPreview(probe.data.uuid)
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(busy = false, message = probe.error.userMessage()) }
                }
            }
        }
    }

    private suspend fun loadDashcamStatus() {
        _uiState.update { it.copy(busy = true, message = null) }
        runCatching {
            val storage = dashcamRepository.getStorageStatus()
            val settings = settingsRepository.getSettings()
            _uiState.update {
                it.copy(
                    busy = false,
                    storageStatus = (storage as? AppResult.Success)?.data,
                    settings = (settings as? AppResult.Success)?.data,
                    message = (storage as? AppResult.Failure)?.error?.userMessage()
                        ?: (settings as? AppResult.Failure)?.error?.userMessage()
                )
            }
        }.onFailure { throwable ->
            _uiState.update { it.copy(busy = false, message = AppError.Unknown(throwable).userMessage()) }
        }
    }

    fun startPreview() {
        viewModelScope.launch {
            startPreviewInternal()
        }
    }

    fun resumePreview() {
        viewModelScope.launch {
            if (_uiState.value.connectionState !is DashcamConnectionState.Connected || isPreviewActive()) return@launch
            startPreviewInternal()
        }
    }

    fun switchCamera(camera: DashcamCamera) {
        viewModelScope.launch {
            switchCameraInternal(camera)
        }
    }

    fun snapshot() {
        viewModelScope.launch {
            when (val result = takeSnapshot()) {
                is AppResult.Success -> _uiState.update { it.copy(message = AppNotice.SnapshotSaved.userMessage(), snapshotSuccess = true) }
                is AppResult.Failure -> _uiState.update { it.copy(message = AppNotice.SnapshotFailed.userMessage(), snapshotSuccess = false) }
            }
        }
    }

    fun requestRecording(enabled: Boolean) {
        if (!enabled) _uiState.update { it.copy(confirmStopRecording = true) } else setRecording(true)
    }

    fun confirmStopRecording() {
        _uiState.update { it.copy(confirmStopRecording = false) }
        setRecording(false)
    }

    fun dismissStopRecording() {
        _uiState.update { it.copy(confirmStopRecording = false) }
    }

    private fun setRecording(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = recordingUseCase(enabled)) {
                is AppResult.Success -> _uiState.update { it.copy(settings = result.data, message = recordingUserMessage(enabled)) }
                is AppResult.Failure -> _uiState.update { it.copy(message = result.error.userMessage()) }
            }
        }
    }

    private suspend fun maybeAutoStartPreview(deviceUuid: String) {
        val latestPreferences = preferencesDataSource.preferences.first()
        preferences = latestPreferences
        _uiState.update { it.copy(selectedCamera = latestPreferences.preferredCamera) }
        if (!latestPreferences.autoStartLivePreview || autoStartedForDeviceUuid == deviceUuid || isPreviewActive()) return
        autoStartedForDeviceUuid = deviceUuid
        startPreviewInternal()
    }

    private suspend fun startPreviewInternal() {
        previewCommandMutex.withLock {
            _uiState.update { it.copy(busy = true, message = null) }
            when (val result = prepareLivePreview()) {
                is AppResult.Success -> {
                    val preferred = _uiState.value.selectedCamera
                    when (val startResult = switchCameraUseCase(preferred)) {
                        is AppResult.Success -> _uiState.update { it.copy(busy = false) }
                        is AppResult.Failure -> _uiState.update { it.copy(busy = false, message = startResult.error.userMessage()) }
                    }
                }
                is AppResult.Failure -> _uiState.update { it.copy(busy = false, message = result.error.userMessage()) }
            }
        }
    }

    private suspend fun switchCameraInternal(camera: DashcamCamera) {
        previewCommandMutex.withLock {
            if (camera == _uiState.value.selectedCamera && _uiState.value.previewState == LivePreviewState.Playing) return
            _uiState.update { it.copy(busy = true, selectedCamera = camera, message = null) }
            when (val result = switchCameraUseCase(camera)) {
                is AppResult.Success -> _uiState.update { it.copy(busy = false) }
                is AppResult.Failure -> _uiState.update { it.copy(busy = false, message = result.error.userMessage()) }
            }
        }
    }

    private fun isPreviewActive(): Boolean = uiState.value.previewState in setOf(
        LivePreviewState.Preparing,
        LivePreviewState.Connecting,
        LivePreviewState.Buffering,
        LivePreviewState.Playing
    )

    override fun onCleared() {
        livePreviewEngine.release()
        connectionMonitor.stopMonitoring()
        super.onCleared()
    }
}
