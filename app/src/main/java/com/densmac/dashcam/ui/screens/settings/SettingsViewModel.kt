package com.densmac.dashcam.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.core.network.DashcamConnectionMonitor
import com.densmac.dashcam.data.datastore.ThemeMode
import com.densmac.dashcam.data.datastore.UserPreferencesDataSource
import com.densmac.dashcam.domain.model.LevelSetting
import com.densmac.dashcam.domain.model.LoopDuration
import com.densmac.dashcam.domain.model.TimelapseRate
import com.densmac.dashcam.domain.repository.DashcamRepository
import com.densmac.dashcam.domain.repository.SettingsRepository
import com.densmac.dashcam.domain.usecase.StartStopRecordingUseCase
import com.densmac.dashcam.domain.usecase.UpdateSettingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connectionMonitor: DashcamConnectionMonitor,
    private val dashcamRepository: DashcamRepository,
    private val settingsRepository: SettingsRepository,
    private val updateSetting: UpdateSettingUseCase,
    private val recordingUseCase: StartStopRecordingUseCase,
    private val preferencesDataSource: UserPreferencesDataSource
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        connectionMonitor.startMonitoring()
        viewModelScope.launch {
            connectionMonitor.connectionState.collectLatest { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            preferencesDataSource.preferences.collectLatest { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val settings = settingsRepository.getSettings()
            val storage = dashcamRepository.getStorageStatus()
            _uiState.update {
                it.copy(
                    loading = false,
                    dashcamSettings = (settings as? AppResult.Success)?.data,
                    storageStatus = (storage as? AppResult.Success)?.data,
                    message = (settings as? AppResult.Failure)?.error?.userMessage()
                        ?: (storage as? AppResult.Failure)?.error?.userMessage()
                )
            }
        }
    }

    fun setMic(value: Boolean) = updateAndRefresh { updateSetting.mic(value) }
    fun setOsd(value: Boolean) = updateAndRefresh { updateSetting.osd(value) }
    fun setLoop(value: LoopDuration) = updateAndRefresh { updateSetting.loopDuration(value) }
    fun setSpeaker(value: LevelSetting) = updateAndRefresh { updateSetting.speaker(value) }
    fun setGSensor(value: LevelSetting) = updateAndRefresh { updateSetting.gSensor(value) }
    fun setTimelapse(value: TimelapseRate) = updateAndRefresh { updateSetting.timelapse(value) }

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

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { preferencesDataSource.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { preferencesDataSource.setDynamicColorEnabled(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { preferencesDataSource.setHapticsEnabled(value) }
    fun setAutoStart(value: Boolean) = viewModelScope.launch { preferencesDataSource.setAutoStartLivePreview(value) }
    fun setDiagnostics(value: Boolean) = viewModelScope.launch { preferencesDataSource.setShowDebugDiagnostics(value) }
    fun setSwapCamera(value: Boolean) = viewModelScope.launch { preferencesDataSource.setCameraMappingSwapped(value) }

    private fun setRecording(enabled: Boolean) {
        updateAndRefresh { recordingUseCase(enabled).let { if (it is AppResult.Success) AppResult.Success(Unit) else it as AppResult.Failure } }
    }

    private fun updateAndRefresh(block: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            when (val result = block()) {
                is AppResult.Success -> refresh()
                is AppResult.Failure -> _uiState.update { it.copy(loading = false, message = result.error.userMessage()) }
            }
        }
    }

    override fun onCleared() {
        connectionMonitor.stopMonitoring()
        super.onCleared()
    }
}
