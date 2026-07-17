package com.densmac.dashcam.ui.media

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.core.design.haptics.rememberHapticClick
import com.densmac.dashcam.core.player.MediaPlaybackState
import kotlinx.coroutines.delay

/**
 * A play/pause/replay control overlaid on the video surface. While playing it fades out after a
 * moment and reappears when the video is tapped; any non-playing state keeps it visible. Buffering
 * shows a spinner and errors show a retry button. Shared by the inline viewer, the fullscreen
 * player and the transfers player so they behave identically.
 *
 * @param streaming true when a media surface is present behind the overlay (enables tap-to-reveal
 *   and suppresses the "Tap to stream" idle prompt).
 */
@Composable
fun PlaybackOverlay(
    playbackState: MediaPlaybackState,
    streaming: Boolean,
    onTogglePlayPause: () -> Unit,
    onReplay: () -> Unit,
    onRetry: () -> Unit,
    onPlaySelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var controlsVisible by remember { mutableStateOf(true) }
    // Any non-playing state reveals the controls (so the play/replay button is always visible).
    LaunchedEffect(playbackState) {
        if (playbackState != MediaPlaybackState.Playing) controlsVisible = true
    }
    // While playing, fade the controls out after a moment.
    LaunchedEffect(playbackState, controlsVisible) {
        if (playbackState == MediaPlaybackState.Playing && controlsVisible) {
            delay(2500)
            controlsVisible = false
        }
    }
    Box(modifier) {
        // Tap the video to reveal/hide the controls.
        if (streaming) {
            Box(Modifier.matchParentSize().hapticClickable { controlsVisible = !controlsVisible })
        }
        when (playbackState) {
            MediaPlaybackState.Opening, MediaPlaybackState.Buffering ->
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center))
            is MediaPlaybackState.Error ->
                GlassCircleButton(Icons.Outlined.Refresh, "Retry", onRetry, modifier = Modifier.align(Alignment.Center))
            else -> Unit
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = (controlsVisible || !streaming) && playbackState !in setOf(
                MediaPlaybackState.Opening, MediaPlaybackState.Buffering
            ) && playbackState !is MediaPlaybackState.Error,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            when (playbackState) {
                MediaPlaybackState.Playing -> GlassCircleButton(Icons.Outlined.Pause, "Pause", onTogglePlayPause)
                MediaPlaybackState.Paused -> GlassCircleButton(Icons.Outlined.PlayArrow, "Play", onTogglePlayPause)
                MediaPlaybackState.Ended -> GlassCircleButton(Icons.Outlined.Replay, "Replay", onReplay)
                else -> if (!streaming) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlassCircleButton(Icons.Outlined.PlayArrow, "Play", onPlaySelected)
                        Spacer(Modifier.height(10.dp))
                        Text("Tap to stream", color = Color(0xFFBFAE8F), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val click = rememberHapticClick(onClick = onClick)
    IconButton(
        onClick = click,
        modifier = modifier
            .size(66.dp)
            .clip(RoundedCornerShape(33.dp))
            .background(Color(0x59000000))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(33.dp))
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFFF7E7C7), modifier = Modifier.size(32.dp))
    }
}
