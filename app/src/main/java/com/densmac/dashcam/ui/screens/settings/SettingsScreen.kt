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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.densmac.dashcam.core.design.components.DashcamButtonStyle
import com.densmac.dashcam.core.design.components.GlassCard
import com.densmac.dashcam.core.design.components.SectionHeader
import androidx.compose.ui.platform.LocalContext
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.core.design.haptics.rememberHapticClick
import com.densmac.dashcam.ui.media.appLabel
import com.densmac.dashcam.ui.media.videoPlayerApps
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
                .height(56.dp)
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        ConnectionSection(state)
        NetworkSection(state, viewModel)
        RecordingSection(state, viewModel)
        SoundSection(state, viewModel)
        StorageSection(state, viewModel)
        AppearanceSection(state, viewModel)
        DiagnosticsSection(state, viewModel)
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
    if (state.editWifiSsid) {
        WifiTextDialog(
            title = "Wi-Fi name",
            label = "New network name",
            initial = state.currentSsid.orEmpty(),
            confirmEnabled = { it.matches(SSID_REGEX) },
            helper = "4–22 letters and numbers, no spaces or symbols.",
            onConfirm = viewModel::submitWifiSsid,
            onDismiss = viewModel::dismissWifiDialogs
        )
    }
    if (state.editWifiPassword) {
        WifiPasswordDialog(
            onConfirm = viewModel::submitWifiPassword,
            onDismiss = viewModel::dismissWifiDialogs
        )
    }
    if (state.confirmFormat) {
        ConfirmDangerDialog(
            title = "Format SD card?",
            message = "This permanently erases all recordings on the dashcam SD card. This cannot be undone.",
            confirmText = "Erase everything",
            onConfirm = viewModel::confirmFormat,
            onDismiss = viewModel::dismissFormat
        )
    }
}

private val SSID_REGEX = Regex("^[A-Za-z0-9]{4,22}$")
private val WIFI_PWD_REGEX = Regex("^[A-Za-z0-9]{8,22}$")

@Composable
private fun NetworkSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val connected = state.connectionState is DashcamConnectionState.Connected
    GlassCard {
        SectionHeader("Network")
        Spacer(Modifier.height(6.dp))
        NavRow(
            title = "Wi-Fi name",
            value = state.currentSsid ?: "—",
            enabled = connected,
            onClick = viewModel::requestEditWifiSsid
        )
        NavRow(
            title = "Wi-Fi password",
            value = "••••••••",
            enabled = connected,
            onClick = viewModel::requestEditWifiPassword
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Changing either restarts the dashcam Wi-Fi; you'll need to reconnect this phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NavRow(title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    val click = rememberHapticClick(onClick = onClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.hapticClickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
private fun StorageSection(state: SettingsUiState, viewModel: SettingsViewModel) {
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
        Spacer(Modifier.height(14.dp))
        DashcamButton(
            "Format SD card",
            viewModel::requestFormat,
            icon = Icons.Outlined.DeleteForever,
            style = DashcamButtonStyle.Outline,
            enabled = state.connectionState is DashcamConnectionState.Connected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WifiTextDialog(
    title: String,
    label: String,
    initial: String,
    confirmEnabled: (String) -> Boolean,
    helper: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(label) }
                )
                Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = confirmEnabled(value)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WifiPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    val valid = pwd.matches(WIFI_PWD_REGEX) && pwd == confirmPwd
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("New password") }
                )
                OutlinedTextField(
                    value = confirmPwd,
                    onValueChange = { confirmPwd = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Confirm password") }
                )
                Text(
                    if (confirmPwd.isNotEmpty() && pwd != confirmPwd) "Passwords don't match."
                    else "8–22 letters and numbers, no spaces or symbols.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (confirmPwd.isNotEmpty() && pwd != confirmPwd) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pwd) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AppearanceSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var showPlayerPicker by remember { mutableStateOf(false) }
    GlassCard {
        SectionHeader("Appearance")
        Spacer(Modifier.height(6.dp))
        // Theme mode lives in the top-bar switch; keep only the rest here to avoid a duplicate control.
        ToggleRow("Dynamic color", state.preferences.dynamicColorEnabled, viewModel::setDynamicColor)
        ToggleRow("Haptics", state.preferences.hapticsEnabled, viewModel::setHaptics)
        ToggleRow("Auto-start live preview", state.preferences.autoStartLivePreview, viewModel::setAutoStart)
        ToggleRow("Swap front/rear labels", state.preferences.cameraMappingSwapped, viewModel::setSwapCamera)
        NavRow(
            title = "Default player",
            value = appLabel(context, state.preferences.externalPlayerPackage) ?: "Always ask",
            enabled = true,
            onClick = { showPlayerPicker = true }
        )
    }
    if (showPlayerPicker) {
        ExternalPlayerPickerDialog(
            context = context,
            current = state.preferences.externalPlayerPackage,
            onPick = { viewModel.setExternalPlayer(it); showPlayerPicker = false },
            onDismiss = { showPlayerPicker = false }
        )
    }
}

@Composable
private fun ExternalPlayerPickerDialog(
    context: android.content.Context,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val apps = remember { videoPlayerApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default player") },
        text = {
            Column {
                PickerRow("Always ask", selected = current == null) { onPick(null) }
                apps.forEach { app ->
                    PickerRow(app.label, selected = current == app.packageName) { onPick(app.packageName) }
                }
                if (apps.isEmpty()) {
                    Text("No video apps installed.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .hapticClickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
        if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
