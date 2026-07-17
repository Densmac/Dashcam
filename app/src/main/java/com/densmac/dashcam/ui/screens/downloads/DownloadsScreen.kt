package com.densmac.dashcam.ui.screens.downloads

import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.dashcam.core.design.components.ConfirmDangerDialog
import com.densmac.dashcam.core.design.components.DashcamButton
import com.densmac.dashcam.core.design.components.DashcamButtonStyle
import com.densmac.dashcam.core.design.components.GlassCard
import com.densmac.dashcam.core.design.haptics.HapticEvent
import com.densmac.dashcam.core.design.haptics.LocalDashcamHapticsEnabled
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.core.design.haptics.rememberDashcamHaptics
import com.densmac.dashcam.core.design.haptics.rememberHapticClick
import com.densmac.dashcam.core.player.MediaPlaybackState
import com.densmac.dashcam.domain.model.DownloadItem
import com.densmac.dashcam.domain.model.DownloadStatus
import com.densmac.dashcam.ui.media.MediaScrubber
import com.densmac.dashcam.ui.media.PlaybackOverlay
import com.densmac.dashcam.ui.media.openDashcamFileExternally
import com.densmac.dashcam.ui.media.rememberSwipeZoom
import com.densmac.dashcam.ui.media.shareDashcamFile
import com.densmac.dashcam.ui.media.swipeZoom

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val active = state.activeDownloads
    val finished = state.finishedDownloads

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp)
    ) {
        if (state.downloads.isEmpty()) {
            item { EmptyTransfers() }
        }

        if (active.isNotEmpty()) {
            item { GroupLabel("In progress") }
            items(active, key = { it.id }) { item ->
                TransferCard(item, state.externalPlayerPackage, viewModel)
            }
        }
        if (finished.isNotEmpty()) {
            item { GroupLabel("Saved") }
            items(finished, key = { it.id }) { item ->
                TransferCard(item, state.externalPlayerPackage, viewModel)
            }
        }
    }

    state.playing?.let { item ->
        val progress by viewModel.mediaPlayer.progress.collectAsStateWithLifecycle()
        LocalPlayerDialog(
            item = item,
            playbackState = state.playbackState,
            progress = progress,
            externalPlayerPackage = state.externalPlayerPackage,
            onClose = viewModel::closePlayer,
            onRetry = viewModel::retryPlayback,
            onTogglePlayPause = viewModel::togglePlayPause,
            onReplay = viewModel::replay,
            onSwipeNext = viewModel::playNext,
            onSwipePrevious = viewModel::playPrevious,
            onScrubStart = viewModel::beginScrub,
            onScrubMove = viewModel::previewScrub,
            onScrubEnd = viewModel::seekTo,
            attachPlayer = { host -> viewModel.mediaPlayer.attach(host) }
        )
    }

    if (state.pendingDeleteId != null) {
        ConfirmDangerDialog(
            title = "Delete local file?",
            message = "This removes the saved copy from this phone. It does not delete the dashcam SD-card recording.",
            confirmText = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
private fun TransferCard(item: DownloadItem, externalPlayerPackage: String?, viewModel: DownloadsViewModel) {
    val context = LocalContext.current
    val completed = item.status == DownloadStatus.COMPLETED
    val isActive = item.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED)

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.remotePath.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    "${item.folder.displayName} · ${item.camera?.displayName ?: "Unknown"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            StatusPill(item.status)
        }

        if (isActive) {
            Spacer(Modifier.height(14.dp))
            // Smooth, non-twitchy progress: animate between the discrete DB updates.
            val target = if (item.status == DownloadStatus.QUEUED) 0f else item.progress
            val animated by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 600),
                label = "transfer-progress"
            )
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                progressLabel(item),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                completed -> {
                    DashcamButton("Play", { viewModel.play(item) }, icon = Icons.Outlined.PlayArrow, modifier = Modifier.weight(1f))
                    CircleIconButton(Icons.Outlined.Share, "Share", { shareDashcamFile(context, item.localPath) })
                    CircleIconButton(Icons.Outlined.OpenInNew, "Open externally", { openDashcamFileExternally(context, item.localPath, externalPlayerPackage) })
                }
                item.status == DownloadStatus.FAILED ->
                    DashcamButton("Retry", { viewModel.retry(item.id) }, icon = Icons.Outlined.Refresh, modifier = Modifier.weight(1f))
                isActive ->
                    DashcamButton("Cancel", { viewModel.cancel(item.id) }, icon = Icons.Outlined.Stop, style = DashcamButtonStyle.Outline, modifier = Modifier.weight(1f))
            }
            CircleIconButton(Icons.Outlined.Delete, "Delete", { viewModel.requestDelete(item.id) }, tint = MaterialTheme.colorScheme.error)
        }

        item.errorMessage?.takeIf { item.status == DownloadStatus.FAILED }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// Horizontal drag distance (px) that commits a prev/next saved-clip swipe.
private const val DOWNLOAD_SWIPE_THRESHOLD_PX = 120f

private fun progressLabel(item: DownloadItem): String {
    val pct = (item.progress * 100).toInt()
    val done = item.bytesDownloaded.toDisplayMb()
    val total = item.totalBytes?.toDisplayMb()
    return when (item.status) {
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.PAUSED -> "Paused · $done"
        else -> if (total != null) "$pct%  ·  $done / $total" else done
    }
}

private fun Long.toDisplayMb(): String = "%.1f MB".format(this / 1_000_000.0)

@Composable
private fun StatusPill(status: DownloadStatus) {
    val (label, color) = when (status) {
        DownloadStatus.COMPLETED -> "Saved" to MaterialTheme.colorScheme.primary
        DownloadStatus.RUNNING -> "Downloading" to MaterialTheme.colorScheme.secondary
        DownloadStatus.QUEUED -> "Queued" to MaterialTheme.colorScheme.secondary
        DownloadStatus.PAUSED -> "Paused" to MaterialTheme.colorScheme.secondary
        DownloadStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val hapticClick = rememberHapticClick(onClick = onClick)
    IconButton(
        onClick = hapticClick,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(tint.copy(alpha = 0.12f))
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmptyTransfers() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.size(46.dp))
            }
            Text("No transfers yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Downloads from the library land here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LocalPlayerDialog(
    item: DownloadItem,
    playbackState: MediaPlaybackState,
    progress: com.densmac.dashcam.core.player.MediaProgress,
    externalPlayerPackage: String?,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onReplay: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    attachPlayer: (FrameLayout) -> Unit
) {
    val context = LocalContext.current
    val zoom = rememberSwipeZoom()
    val haptics = rememberDashcamHaptics(LocalDashcamHapticsEnabled.current)
    var landscape by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Force landscape while the fullscreen toggle is on; restore on exit.
        if (landscape) {
            DisposableEffect(Unit) {
                val activity = context.findActivity()
                val previous = activity?.requestedOrientation
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                onDispose {
                    activity?.requestedOrientation = previous ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2050505))
        ) {
            Column(Modifier.fillMaxSize()) {
                // Presentable header bar so the clip name reads as a title, not floating text.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33000000))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = rememberHapticClick(onClick = onClose)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color(0xFFF7E7C7))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            prettyClipName(item.remotePath),
                            color = Color(0xFFF7E7C7),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            "${item.folder.displayName} · ${item.camera?.displayName ?: "Recording"}",
                            color = Color(0xFFBFAE8F),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = rememberHapticClick(onClick = { landscape = !landscape })) {
                        Icon(
                            if (landscape) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            contentDescription = if (landscape) "Exit fullscreen" else "Fullscreen",
                            tint = Color(0xFFF7E7C7)
                        )
                    }
                    IconButton(onClick = rememberHapticClick(onClick = { openDashcamFileExternally(context, item.localPath, externalPlayerPackage) })) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open externally", tint = Color(0xFFF7E7C7))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // Swipe across the video to move to the previous / next saved clip.
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onDragEnd = {
                                    if (totalDrag <= -DOWNLOAD_SWIPE_THRESHOLD_PX) {
                                        haptics(HapticEvent.Confirmation); zoom.animate(1) { onSwipeNext() }
                                    } else if (totalDrag >= DOWNLOAD_SWIPE_THRESHOLD_PX) {
                                        haptics(HapticEvent.Confirmation); zoom.animate(-1) { onSwipePrevious() }
                                    }
                                }
                            ) { _, dragAmount -> totalDrag += dragAmount }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Zoom transition transform applied to the video + overlay.
                    Box(
                        modifier = Modifier.swipeZoom(zoom),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            factory = { ctx -> FrameLayout(ctx).also(attachPlayer) }
                        )
                        // Same auto-fading play/pause/replay control as the clip viewer.
                        PlaybackOverlay(
                            playbackState = playbackState,
                            streaming = true,
                            onTogglePlayPause = onTogglePlayPause,
                            onReplay = onReplay,
                            onRetry = onRetry,
                            onPlaySelected = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                    }
                }
                MediaScrubber(
                    progress = progress,
                    onScrubStart = onScrubStart,
                    onScrubMove = onScrubMove,
                    onScrubEnd = onScrubEnd,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Turn a raw dashcam filename (e.g. 20260716_194811_14_f.ts) into a readable title. */
private fun prettyClipName(remotePath: String): String {
    val base = remotePath.substringAfterLast('/').removeSuffix(".ts")
    val match = Regex("(\\d{4})(\\d{2})(\\d{2})_(\\d{2})(\\d{2})(\\d{2})").find(base) ?: return base
    val (y, mo, d, h, mi, s) = match.destructured
    return "$y-$mo-$d · $h:$mi:$s"
}
