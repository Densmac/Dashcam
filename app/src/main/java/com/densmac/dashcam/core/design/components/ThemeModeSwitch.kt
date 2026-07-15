package com.densmac.dashcam.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.data.datastore.ThemeMode

/**
 * Compact, elegant Light / Auto / Dark switch. Three small icon segments in a pill; the active
 * one gets a tonal chip. Small footprint so it lives in the top bar without crowding the mark
 * or the live preview. Persisting the choice is the caller's job (via [onSelect]).
 */
@Composable
fun ThemeModeSwitch(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Segment(Icons.Outlined.LightMode, "Light theme", selected == ThemeMode.LIGHT) { onSelect(ThemeMode.LIGHT) }
        Segment(Icons.Outlined.BrightnessAuto, "System theme", selected == ThemeMode.SYSTEM) { onSelect(ThemeMode.SYSTEM) }
        Segment(Icons.Outlined.DarkMode, "Dark theme", selected == ThemeMode.DARK) { onSelect(ThemeMode.DARK) }
    }
}

@Composable
private fun Segment(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "theme-seg-bg"
    )
    val tint by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "theme-seg-tint"
    )
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .hapticClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(17.dp))
    }
}
