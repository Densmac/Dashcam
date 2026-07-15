package com.densmac.dashcam.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.common.mbToDisplayGb
import com.densmac.dashcam.core.design.components.ConfirmDangerDialog
import com.densmac.dashcam.core.design.components.DashcamButton
import com.densmac.dashcam.core.design.components.GlassCard
import com.densmac.dashcam.core.design.components.SectionHeader
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.core.design.haptics.rememberHapticClick
import com.densmac.dashcam.data.datastore.ThemeMode
import com.densmac.dashcam.domain.model.DashcamConnectionState
import com.densmac.dashcam.domain.model.LevelSetting
import com.densmac.dashcam.domain.model.LoopDuration
import com.densmac.dashcam.domain.model.TimelapseRate

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backClick = rememberHapticClick { onBack?.invoke() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = backClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                "Settings",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.displaySmall
            )
        }
        ConnectionSection(state)
        RecordingSection(state, viewModel)
        SoundSection(state, viewModel)
        StorageSection(state)
        AppearanceSection(state, viewModel)
        DiagnosticsSection(state, viewModel)
        DeferredSection()
        DashcamButton("Refresh", viewModel::refresh, modifier = Modifier.fillMaxWidth())
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (state.confirmStopRecording) {
        ConfirmDangerDialog(
            title = "Stop recording?",
            message = "The dashcam will stop saving new footage until recording is turned back on.",
            confirmText = "Stop Recording",
            onConfirm = viewModel::confirmStopRecording,
            onDismiss = viewModel::dismissStopRecording
        )
    }
}

@Composable
private fun ConnectionSection(state: SettingsUiState) {
    GlassCard {
        SectionHeader("Connection")
        Spacer(Modifier.height(14.dp))
        val text = when (val connection = state.connectionState) {
            is DashcamConnectionState.Connected -> "${connection.device.model ?: "YantopCam"} - ${connection.device.uuid}"
            is DashcamConnectionState.ApiUnreachable -> connection.message
            DashcamConnectionState.Searching -> "Searching for DASHCAM Wi-Fi"
            DashcamConnectionState.Unknown -> "Unknown"
            DashcamConnectionState.NotConnectedToWifi -> "Not connected to DASHCAM Wi-Fi"
            is DashcamConnectionState.WrongWifi -> "Wrong Wi-Fi: ${connection.currentSsid ?: "unknown"}"
        }
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordingSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = state.dashcamSettings
    GlassCard {
        SectionHeader("Recording")
        ToggleRow("Recording", settings?.recordingEnabled == true, viewModel::requestRecording)
        settings?.loopDuration?.let { current ->
            Spacer(Modifier.height(14.dp))
            Text("Loop duration", fontWeight = FontWeight.SemiBold)
            ChoiceRow(LoopDuration.entries, current, { it.label }, viewModel::setLoop)
        }
        settings?.timelapseRate?.let { current ->
            Spacer(Modifier.height(14.dp))
            Text("Timelapse", fontWeight = FontWeight.SemiBold)
            ChoiceRow(TimelapseRate.entries, current, { it.label }, viewModel::setTimelapse)
        }
        settings?.resolutionLabel?.let {
            Spacer(Modifier.height(14.dp))
            Text("Resolution: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SoundSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = state.dashcamSettings
    GlassCard {
        SectionHeader("Sound and overlay")
        settings?.micEnabled?.let { ToggleRow("Mic", it, viewModel::setMic) }
        settings?.osdEnabled?.let { ToggleRow("OSD", it, viewModel::setOsd) }
        settings?.speakerLevel?.let {
            Spacer(Modifier.height(10.dp))
            Text("Speaker", fontWeight = FontWeight.SemiBold)
            ChoiceRow(LevelSetting.entries, it, { level -> level.label }, viewModel::setSpeaker)
        }
        settings?.gSensorSensitivity?.let {
            Spacer(Modifier.height(10.dp))
            Text("G-sensor", fontWeight = FontWeight.SemiBold)
            ChoiceRow(LevelSetting.entries, it, { level -> level.label }, viewModel::setGSensor)
        }
    }
}

@Composable
private fun StorageSection(state: SettingsUiState) {
    GlassCard {
        SectionHeader("Storage")
        Spacer(Modifier.height(14.dp))
        val storage = state.storageStatus
        if (storage == null) {
            Text("Unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LinearProgressIndicator(progress = storage.usedPercent, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("${storage.freeMb.mbToDisplayGb()} free of ${storage.totalMb.mbToDisplayGb()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppearanceSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    GlassCard {
        SectionHeader("Appearance")
        Spacer(Modifier.height(12.dp))
        Text("Theme", fontWeight = FontWeight.SemiBold)
        ChoiceRow(ThemeMode.entries, state.preferences.themeMode, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, viewModel::setThemeMode)
        ToggleRow("Dynamic color", state.preferences.dynamicColorEnabled, viewModel::setDynamicColor)
        ToggleRow("Haptics", state.preferences.hapticsEnabled, viewModel::setHaptics)
        ToggleRow("Auto-start live preview", state.preferences.autoStartLivePreview, viewModel::setAutoStart)
        ToggleRow("Swap front/rear labels", state.preferences.cameraMappingSwapped, viewModel::setSwapCamera)
    }
}

@Composable
private fun DiagnosticsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    GlassCard {
        SectionHeader("Diagnostics")
        ToggleRow("Show debug diagnostics", state.preferences.showDebugDiagnostics, viewModel::setDiagnostics)
        if (state.preferences.showDebugDiagnostics) {
            Spacer(Modifier.height(12.dp))
            Text("HTTP base: ${DashcamConstants.HTTP_BASE_URL}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("RTSP URL: ${DashcamConstants.RTSP_TRACK2_URL}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Last known device: ${state.preferences.lastKnownDeviceUuid ?: "None"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeferredSection() {
    GlassCard {
        SectionHeader("Deferred")
        Spacer(Modifier.height(12.dp))
        listOf(
            "Lock/protect video",
            "Unlock video",
            "Format SD card",
            "Change Wi-Fi password",
            "Change Wi-Fi SSID"
        ).forEach {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val hapticChange = rememberHapticClick { onChange(!checked) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = { hapticChange() })
    }
}

@Composable
private fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        values.forEach { value ->
            val active = selected == value
            Row(
                modifier = Modifier
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        if (active) {
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.66f)))
                        } else {
                            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)))
                        }
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f), RoundedCornerShape(25.dp))
                    .hapticClickable { onSelected(value) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label(value),
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
