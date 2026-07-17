package com.densmac.dashcam.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.densmac.dashcam.core.player.MediaProgress
import java.util.Locale

/**
 * A seek bar for the in-app media player. Shows elapsed / total time and lets the user scrub to
 * any point. During a drag the thumb follows the finger locally ([onScrubMove]); the actual seek
 * is committed on release ([onScrubEnd]) so playback jumps to the chosen point.
 */
@Composable
fun MediaScrubber(
    progress: MediaProgress,
    onScrubStart: () -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color = Color(0xFFCFC7B6)
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    // Driven by LibVLC position (0..1), which is valid for a seekable Range stream even without a
    // known duration. Enabled whenever the media is seekable.
    val value = (dragValue ?: progress.position).coerceIn(0f, 1f)
    val shownPositionMs = if (dragValue != null && progress.durationMs > 0) {
        (value * progress.durationMs).toLong()
    } else {
        progress.positionMs
    }

    Column(modifier.fillMaxWidth()) {
        Slider(
            value = value,
            enabled = progress.seekable || progress.durationMs > 0,
            onValueChange = { v ->
                if (dragValue == null) onScrubStart()
                dragValue = v
                onScrubMove(v)
            },
            onValueChangeFinished = {
                dragValue?.let { onScrubEnd(it) }
                dragValue = null
            },
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(shownPositionMs), color = labelColor, style = MaterialTheme.typography.labelSmall)
            Text(
                if (progress.durationMs > 0) formatTime(progress.durationMs) else "––:––",
                color = labelColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
