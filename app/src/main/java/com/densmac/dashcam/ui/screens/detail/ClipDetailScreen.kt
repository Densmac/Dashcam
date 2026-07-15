package com.densmac.dashcam.ui.screens.detail

import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.dashcam.core.common.DateTimeFormatters
import com.densmac.dashcam.core.common.kbToDisplayMb
import com.densmac.dashcam.core.design.components.ConfirmDangerDialog
import com.densmac.dashcam.core.design.components.DashcamButton
import com.densmac.dashcam.core.design.components.DashcamButtonStyle
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.core.design.haptics.rememberHapticClick
import com.densmac.dashcam.core.player.MediaPlaybackState
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.ui.media.openDashcamFileExternally

@Composable
fun ClipDetailScreen(
    onBack: () -> Unit,
    viewModel: ClipDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bundle = state.bundle
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        MinimalTopBar(
            title = bundle?.let { DateTimeFormatters.displayCameraTime(it.startTime) } ?: "Clip",
            onBack = onBack
        )

        if (bundle == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.loading) "Loading clip…" else "Clip not found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            MediaStage(
                state = state,
                onSelectSide = viewModel::selectSide,
                onPlaySelected = { state.selectedFile?.let(viewModel::stream) },
                onRetry = viewModel::retryStream,
                onTogglePlayPause = viewModel::togglePlayPause,
                attachPlayer = { host -> viewModel.mediaPlayer.attach(host) }
            )

            SelectedInfo(state = state)

            ActionBar(
                state = state,
                onDownloadSelected = { state.selectedFile?.let { viewModel.download(listOf(it)) } },
                onOpenSelected = { openDashcamFileExternally(context, state.localPathFor(state.selectedFile)) },
                onDeleteSelected = { state.selectedFile?.let { viewModel.requestDelete(listOf(it)) } }
            )

            if (bundle.front != null && bundle.rear != null) {
                val bothDownloaded = state.localPathFor(bundle.front) != null && state.localPathFor(bundle.rear) != null
                if (!bothDownloaded) {
                    val downloadBoth = rememberHapticClick {
                        viewModel.download(listOfNotNull(bundle.front, bundle.rear))
                    }
                    Text(
                        "Download both",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(20.dp))
                            .hapticClickable(onClick = { viewModel.download(listOfNotNull(bundle.front, bundle.rear)) })
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    DisposableEffect(Unit) { onDispose { viewModel.stopStream() } }

    if (state.pendingDelete.isNotEmpty()) {
        ConfirmDangerDialog(
            title = "Delete recording?",
            message = "This removes the selected file from the dashcam SD card. This cannot be undone.",
            confirmText = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

@Composable
private fun MinimalTopBar(title: String, onBack: () -> Unit) {
    val backClick = rememberHapticClick(onClick = onBack)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        IconButton(onClick = backClick, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
        }
        Text(
            title,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MediaStage(
    state: ClipDetailUiState,
    onSelectSide: (DashcamCamera) -> Unit,
    onPlaySelected: () -> Unit,
    onRetry: () -> Unit,
    onTogglePlayPause: () -> Unit,
    attachPlayer: (FrameLayout) -> Unit
) {
    val bundle = state.bundle
    val streaming = state.streamingFile != null
    val downloaded = state.localPathFor(state.selectedFile) != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1510), Color(0xFF0A0B08))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
    ) {
        if (streaming) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> FrameLayout(ctx).also(attachPlayer) }
            )
        }

        // Center overlay by playback state.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = state.playbackState, label = "media-overlay") { playback ->
                when (playback) {
                    MediaPlaybackState.Opening,
                    MediaPlaybackState.Buffering ->
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    is MediaPlaybackState.Error ->
                        GlassCircleButton(Icons.Outlined.Refresh, "Retry", onRetry)
                    MediaPlaybackState.Paused ->
                        GlassCircleButton(Icons.Outlined.PlayArrow, "Play", onTogglePlayPause)
                    MediaPlaybackState.Playing ->
                        Box(Modifier.fillMaxSize().hapticClickable(onClick = onTogglePlayPause))
                    else ->
                        if (!streaming) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                GlassCircleButton(Icons.Outlined.PlayArrow, "Play", onPlaySelected)
                                Spacer(Modifier.height(10.dp))
                                Text("Tap to stream", color = Color(0xFFBFAE8F), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                }
            }
        }

        if (downloaded) {
            Text(
                "OFFLINE",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // Sleek front/rear segmented control, overlaid bottom-center.
        if (bundle?.front != null && bundle.rear != null) {
            SideSegmented(
                selected = state.selectedSide,
                onSelect = onSelectSide,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun SideSegmented(
    selected: DashcamCamera,
    onSelect: (DashcamCamera) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x99101010))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SideSegment("Front", selected == DashcamCamera.FRONT) { onSelect(DashcamCamera.FRONT) }
        SideSegment("Rear", selected == DashcamCamera.REAR) { onSelect(DashcamCamera.REAR) }
    }
}

@Composable
private fun SideSegment(label: String, active: Boolean, onClick: () -> Unit) {
    val bg by animateFloatAsState(if (active) 1f else 0f, label = "seg-bg")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = bg))
            .hapticClickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) MaterialTheme.colorScheme.onPrimary else Color(0xFFEBD8B9),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SelectedInfo(state: ClipDetailUiState) {
    val file = state.selectedFile ?: return
    val bundle = state.bundle
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "${if (state.selectedSide == DashcamCamera.FRONT) "Front" else "Rear"} camera",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "${bundle?.folder?.displayName ?: "Recording"} · ${file.sizeKb.kbToDisplayMb()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ActionBar(
    state: ClipDetailUiState,
    onDownloadSelected: () -> Unit,
    onOpenSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val downloaded = state.localPathFor(state.selectedFile) != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (downloaded) {
            DashcamButton("Open externally", onOpenSelected, icon = Icons.Outlined.OpenInNew, modifier = Modifier.weight(1f))
        } else {
            DashcamButton("Download", onDownloadSelected, icon = Icons.Outlined.Download, modifier = Modifier.weight(1f))
        }
        val deleteClick = rememberHapticClick(onClick = onDeleteSelected)
        IconButton(
            onClick = deleteClick,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f))
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun GlassCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val click = rememberHapticClick(onClick = onClick)
    IconButton(
        onClick = click,
        modifier = Modifier
            .size(66.dp)
            .clip(RoundedCornerShape(33.dp))
            .background(Color(0x59000000))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(33.dp))
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFFF7E7C7), modifier = Modifier.size(32.dp))
    }
}
