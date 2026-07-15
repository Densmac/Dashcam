package com.densmac.dashcam.ui.screens.live

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.dashcam.core.common.mbToDisplayGb
import com.densmac.dashcam.core.design.components.ConfirmDangerDialog
import com.densmac.dashcam.core.design.components.StatusPill
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.core.design.haptics.rememberHapticClick
import com.densmac.dashcam.core.player.LivePreviewState
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamConnectionState
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun LiveScreen(
    isVisible: Boolean,
    onOpenLibrary: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val engine = viewModel.livePreviewEngine
    val context = LocalContext.current

    LaunchedEffect(isVisible) {
        if (isVisible) viewModel.resumePreview() else engine.release()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CinematicLivePanel(
            state = state,
            onConnectionClick = {
                if (state.connectionState is DashcamConnectionState.Connected) {
                    viewModel.refreshStatus()
                } else {
                    context.startActivity(wifiIntent())
                }
            },
            onStartPreview = viewModel::startPreview,
            onCamera = viewModel::switchCamera,
            onSnapshot = viewModel::snapshot,
            onReconnect = viewModel::startPreview,
            onRecording = viewModel::requestRecording,
            onOpenLibrary = onOpenLibrary,
            attachEngine = { host -> engine.attach(host) }
        )
    }

    DisposableEffect(Unit) {
        onDispose { engine.release() }
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
private fun CinematicLivePanel(
    state: LiveUiState,
    onConnectionClick: () -> Unit,
    onStartPreview: () -> Unit,
    onCamera: (DashcamCamera) -> Unit,
    onSnapshot: () -> Unit,
    onReconnect: () -> Unit,
    onRecording: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
    attachEngine: (FrameLayout) -> Unit
) {
    val showStatic = state.previewState != LivePreviewState.Playing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 650.dp)
            .clip(RoundedCornerShape(36.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        ConnectionPill(state.connectionState, onConnectionClick)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF080806))
        ) {
            if (state.previewState.needsPlayerSurface()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context -> FrameLayout(context).also(attachEngine) }
                )
            }

            if (state.previewState != LivePreviewState.Playing) {
                ViewfinderStatic(Modifier.fillMaxSize())
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.12f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            PreviewOverlay(
                state = state.previewState,
                onStart = onStartPreview,
                modifier = Modifier.align(Alignment.Center)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(if (state.selectedCamera == DashcamCamera.FRONT) "F" else "R", MaterialTheme.colorScheme.primary)
                if (state.settings?.recordingEnabled == true) {
                    StatusPill("REC", MaterialTheme.colorScheme.secondary)
                }
            }

            CameraRail(
                selected = state.selectedCamera,
                enabled = !state.busy,
                onSelected = onCamera,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            )
        }

        ControlBar(onSnapshot = onSnapshot)
    }
    }
}

@Composable
private fun ConnectionPill(state: DashcamConnectionState, onClick: () -> Unit) {
    val connected = state is DashcamConnectionState.Connected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(Color(0xF2F7E7C7))
            .hapticClickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (connected) "DASHCAM" else "Wi-Fi",
            color = Color(0xFF171D14),
            style = MaterialTheme.typography.labelLarge
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (connected) MaterialTheme.colorScheme.primary else Color(0xFFEBD8B9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Wifi, contentDescription = null, tint = Color(0xFF12160F), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CameraRail(
    selected: DashcamCamera,
    enabled: Boolean,
    onSelected: (DashcamCamera) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xEAF7E7C7))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DashcamCamera.entries.forEach { camera ->
            val active = selected == camera
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .then(
                        if (enabled && !active) {
                            Modifier.hapticClickable { onSelected(camera) }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (camera == DashcamCamera.FRONT) "F" else "R",
                    color = if (active) MaterialTheme.colorScheme.onPrimary else Color(0xFF11170F),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun ViewfinderStatic(modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            frame += 1
            delay(46)
        }
    }

    Canvas(modifier = modifier) {
        val dot = 1.8.dp.toPx().coerceAtLeast(1f)
        var random = frame * 1_103_515_245 + 12_345

        fun nextFloat(): Float {
            random = random * 1_664_525 + 1_013_904_223
            return ((random ushr 8) and 0xFFFF) / 65_535f
        }

        // Greyish static field (a warm charcoal grey) instead of near-black, so the placeholder
        // reads as TV-style grain rather than a black void.
        drawRect(Color(0xFF3A3934))

        val speckles = min(1_400, ((size.width * size.height) / (26.dp.toPx() * 26.dp.toPx())).toInt().coerceAtLeast(320))
        repeat(speckles) {
            val x = nextFloat() * size.width
            val sy = nextFloat() * size.height
            val roll = nextFloat()
            // Mix light and dark grey grain (plus an occasional warm fleck) for a lively grey noise.
            val color = when {
                roll > 0.62f -> Color(0xFFCFCcC4)
                roll > 0.24f -> Color(0xFF565550)
                else -> Color(0xFFF7E7C7)
            }
            val alpha = 0.10f + nextFloat() * 0.22f
            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, sy),
                size = Size(dot * (1f + nextFloat() * 1.8f), dot)
            )
        }
    }
}

@Composable
private fun ControlBar(
    onSnapshot: () -> Unit
) {
    // A single, elegant snapshot control — centered, no bar background. Theme-aware so it stays
    // legible on the light cream background as well as the dark one.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val hapticClick = rememberHapticClick(onClick = onSnapshot)
        IconButton(
            onClick = hapticClick,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = "Snapshot",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PreviewOverlay(
    state: LivePreviewState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(targetState = state, modifier = modifier, label = "preview-state") { previewState ->
        when (previewState) {
            LivePreviewState.Idle,
            LivePreviewState.Released -> CenterPreviewButton(Icons.Outlined.PlayArrow, "Start preview", onStart)
            LivePreviewState.Preparing,
            LivePreviewState.Connecting,
            LivePreviewState.Buffering -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            is LivePreviewState.Error -> CenterPreviewButton(Icons.Outlined.Refresh, "Retry", onStart)
            LivePreviewState.Playing -> Unit
        }
    }
}

@Composable
private fun CenterPreviewButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val hapticClick = rememberHapticClick(onClick = onClick)
    IconButton(
        onClick = hapticClick,
        modifier = Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(Color(0x66000000))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(31.dp))
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color(0xFFF7E7C7), modifier = Modifier.size(28.dp))
    }
}

private fun wifiIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
    else Intent(Settings.ACTION_WIFI_SETTINGS)

private fun LivePreviewState.needsPlayerSurface(): Boolean = when (this) {
    LivePreviewState.Preparing,
    LivePreviewState.Connecting,
    LivePreviewState.Buffering,
    LivePreviewState.Playing -> true
    LivePreviewState.Idle,
    LivePreviewState.Released,
    is LivePreviewState.Error -> false
}
