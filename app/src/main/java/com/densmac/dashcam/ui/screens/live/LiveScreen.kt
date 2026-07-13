package com.densmac.dashcam.ui.screens.live

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.densmac.dashcam.core.player.LivePreviewState
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamConnectionState
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun LiveScreen(
    onOpenLibrary: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val engine = viewModel.livePreviewEngine
    val context = LocalContext.current

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
            .background(Color(0xFF080806))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(36.dp))
    ) {
        if (showStatic) {
            ViewfinderStatic(Modifier.matchParentSize())
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFC5784D),
                                Color(0xFF695733),
                                Color(0xFF242417),
                                Color(0xFF080907)
                            )
                        )
                    )
            )
        }

        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.05f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.48f)
                        )
                    )
                )
        )

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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> FrameLayout(context).also(attachEngine) }
            )

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
                onSelected = onCamera,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(14.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xE80B0C08))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconDeckButton(Icons.Outlined.PhotoCamera, "Snapshot", onSnapshot)
                IconDeckButton(Icons.Outlined.FiberManualRecord, "Record") {
                    onRecording(state.settings?.recordingEnabled != true)
                }
                IconDeckButton(Icons.Outlined.Refresh, "Reconnect", onReconnect)
                IconDeckButton(Icons.Outlined.VideoLibrary, "Library", onOpenLibrary)
            }
        }

        StorageStrip(state = state, onOpenLibrary = onOpenLibrary)
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
            .clickable(onClick = onClick)
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
                    .clickable { onSelected(camera) },
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
private fun IconDeckButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color(0xFFF7E7C7))
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

        drawRect(Color.Black.copy(alpha = 0.36f))

        val speckles = min(1_000, ((size.width * size.height) / (34.dp.toPx() * 34.dp.toPx())).toInt().coerceAtLeast(220))
        repeat(speckles) {
            val x = nextFloat() * size.width
            val sy = nextFloat() * size.height
            val warm = nextFloat() > 0.28f
            val alpha = 0.06f + nextFloat() * 0.18f
            drawRect(
                color = (if (warm) Color(0xFFF7E7C7) else Color.White).copy(alpha = alpha),
                topLeft = Offset(x, sy),
                size = Size(dot * (1f + nextFloat() * 1.8f), dot)
            )
        }
    }
}

@Composable
private fun StorageStrip(state: LiveUiState, onOpenLibrary: () -> Unit) {
    val storage = state.storageStatus
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xD012130E))
            .clickable(onClick = onOpenLibrary)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        if (storage == null) {
            Text("Vault", color = Color(0xFFF7E7C7), style = MaterialTheme.typography.titleMedium)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${(storage.usedPercent * 100).toInt()}%", color = Color(0xFFF7E7C7), style = MaterialTheme.typography.headlineMedium)
                Text(storage.freeMb.mbToDisplayGb(), color = Color(0xFFBFAE8F))
            }
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
            LivePreviewState.Released -> IconDeckButton(Icons.Outlined.PlayArrow, "Start preview", onStart)
            LivePreviewState.Preparing,
            LivePreviewState.Connecting,
            LivePreviewState.Buffering -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            is LivePreviewState.Error -> IconDeckButton(Icons.Outlined.Refresh, "Retry", onStart)
            LivePreviewState.Playing -> Unit
        }
    }
}

private fun wifiIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
    else Intent(Settings.ACTION_WIFI_SETTINGS)
