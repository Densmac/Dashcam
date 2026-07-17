package com.densmac.dashcam.ui.screens.detail

import android.content.pm.ActivityInfo
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.dashcam.core.common.DateTimeFormatters
import com.densmac.dashcam.core.common.kbToDisplayMb
import com.densmac.dashcam.core.design.components.ConfirmDangerDialog
import com.densmac.dashcam.core.design.components.DashcamButton
import com.densmac.dashcam.core.design.components.DashcamLoading
import com.densmac.dashcam.core.design.components.DashcamButtonStyle
import com.densmac.dashcam.core.design.haptics.HapticEvent
import com.densmac.dashcam.core.design.haptics.LocalDashcamHapticsEnabled
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.core.design.haptics.rememberDashcamHaptics
import com.densmac.dashcam.core.design.haptics.rememberHapticClick
import com.densmac.dashcam.core.player.MediaPlaybackState
import com.densmac.dashcam.core.player.MediaProgress
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.ui.media.MediaScrubber
import com.densmac.dashcam.ui.media.PlaybackOverlay
import com.densmac.dashcam.ui.media.openDashcamFileExternally
import com.densmac.dashcam.ui.media.rememberSwipeZoom
import com.densmac.dashcam.ui.media.swipeZoom

@Composable
fun ClipDetailScreen(
    onBack: () -> Unit,
    viewModel: ClipDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.mediaPlayer.progress.collectAsStateWithLifecycle()
    val bundle = state.bundle
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
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
                if (state.loading) {
                    DashcamLoading()
                } else {
                    Text("Clip not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            MediaStage(
                state = state,
                fullscreen = fullscreen,
                onSelectSide = viewModel::selectSide,
                onPlaySelected = { state.selectedFile?.let(viewModel::stream) },
                onRetry = viewModel::retryStream,
                onTogglePlayPause = viewModel::togglePlayPause,
                onReplay = viewModel::replay,
                onEnterFullscreen = { fullscreen = true },
                onSwipeNext = viewModel::goToNext,
                onSwipePrevious = viewModel::goToPrevious,
                attachPlayer = { host -> viewModel.mediaPlayer.attach(host) }
            )

            if (state.streamingFile != null) {
                MediaScrubber(
                    progress = progress,
                    onScrubStart = viewModel::beginScrub,
                    onScrubMove = viewModel::previewScrub,
                    onScrubEnd = viewModel::seekTo
                )
            }

            SelectedInfo(state = state)

            ActionBar(
                state = state,
                onDownloadSelected = { state.selectedFile?.let { viewModel.download(listOf(it)) } },
                onOpenSelected = { openDashcamFileExternally(context, state.localPathFor(state.selectedFile), state.externalPlayerPackage) },
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
        DownloadQueuedConfirmation(
            tick = state.downloadQueuedTick,
            modifier = Modifier.align(Alignment.Center)
        )
    }

    DisposableEffect(Unit) { onDispose { viewModel.stopStream() } }

    // Re-bind the video surface when returning to the foreground (backgrounding destroys it, which
    // otherwise leaves audio playing over a blank picture).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.mediaPlayer.reattach()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (fullscreen && state.streamingFile != null) {
        FullscreenPlayer(
            playbackState = state.playbackState,
            progress = progress,
            onTogglePlayPause = viewModel::togglePlayPause,
            onReplay = viewModel::replay,
            onRetry = viewModel::retryStream,
            onScrubStart = viewModel::beginScrub,
            onScrubMove = viewModel::previewScrub,
            onScrubEnd = viewModel::seekTo,
            onExit = { fullscreen = false },
            attachPlayer = { host -> viewModel.mediaPlayer.attach(host) }
        )
    }

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
private fun FullscreenPlayer(
    playbackState: MediaPlaybackState,
    progress: MediaProgress,
    onTogglePlayPause: () -> Unit,
    onReplay: () -> Unit,
    onRetry: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onExit: () -> Unit,
    attachPlayer: (FrameLayout) -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Force landscape while fullscreen; restore the previous orientation on exit.
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            val previous = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> FrameLayout(ctx).also(attachPlayer) }
            )
            // Same auto-fading play/pause/replay control as the inline viewer.
            PlaybackOverlay(
                playbackState = playbackState,
                streaming = true,
                onTogglePlayPause = onTogglePlayPause,
                onReplay = onReplay,
                onRetry = onRetry,
                onPlaySelected = {},
                modifier = Modifier.matchParentSize()
            )
            // Exit fullscreen (back to portrait).
            IconButton(
                onClick = rememberHapticClick(onClick = onExit),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x66000000))
            ) {
                Icon(Icons.Outlined.FullscreenExit, contentDescription = "Exit fullscreen", tint = Color(0xFFF7E7C7))
            }

            // Seek bar along the bottom.
            MediaScrubber(
                progress = progress,
                onScrubStart = onScrubStart,
                onScrubMove = onScrubMove,
                onScrubEnd = onScrubEnd,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
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
    fullscreen: Boolean,
    onSelectSide: (DashcamCamera) -> Unit,
    onPlaySelected: () -> Unit,
    onRetry: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onReplay: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    attachPlayer: (FrameLayout) -> Unit
) {
    val bundle = state.bundle
    val streaming = state.streamingFile != null
    val downloaded = state.localPathFor(state.selectedFile) != null
    val zoom = rememberSwipeZoom()
    val haptics = rememberDashcamHaptics(LocalDashcamHapticsEnabled.current)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            // Dramatic zoom-out/advance/zoom-in transform driven by prev/next swipes.
            .swipeZoom(zoom)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1510), Color(0xFF0A0B08))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
            // Swipe across the video to move to the previous / next clip in the folder, with a
            // haptic bump and the zoom transition so the change is felt and seen.
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag <= -SWIPE_THRESHOLD_PX) {
                            haptics(HapticEvent.Confirmation); zoom.animate(1) { onSwipeNext() }
                        } else if (totalDrag >= SWIPE_THRESHOLD_PX) {
                            haptics(HapticEvent.Confirmation); zoom.animate(-1) { onSwipePrevious() }
                        }
                    }
                ) { _, dragAmount -> totalDrag += dragAmount }
            }
    ) {
        if (streaming) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> FrameLayout(ctx).also(attachPlayer) },
                // When returning from fullscreen, re-bind the player to this inline surface.
                update = { host -> if (!fullscreen) attachPlayer(host) }
            )
        }

        PlaybackOverlay(
            playbackState = state.playbackState,
            streaming = streaming,
            onTogglePlayPause = onTogglePlayPause,
            onReplay = onReplay,
            onRetry = onRetry,
            onPlaySelected = onPlaySelected,
            modifier = Modifier.matchParentSize()
        )

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

        // Fullscreen (landscape) toggle, shown once a stream is open.
        if (streaming) {
            IconButton(
                onClick = rememberHapticClick(onClick = onEnterFullscreen),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color(0x66000000))
            ) {
                Icon(Icons.Outlined.Fullscreen, contentDescription = "Fullscreen", tint = Color(0xFFF7E7C7), modifier = Modifier.size(20.dp))
            }
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

/** A brief centered icon that confirms a download was queued (replaces a text notice). */
@Composable
private fun DownloadQueuedConfirmation(tick: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        if (tick > 0) {
            visible = true
            delay(1300)
            visible = false
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
        exit = androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xE6141210))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.DownloadDone,
                contentDescription = "Download queued",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp)
            )
        }
    }
}

// Horizontal drag distance (px) that commits a prev/next clip swipe.
private const val SWIPE_THRESHOLD_PX = 120f

