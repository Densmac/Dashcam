package com.densmac.dashcam.core.design.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.densmac.dashcam.core.design.haptics.rememberHapticClick

enum class DashcamButtonStyle { Primary, Tonal, Outline }

@Composable
fun DashcamButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    style: DashcamButtonStyle = DashcamButtonStyle.Primary
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        }
        Text(text)
    }
    val padding = PaddingValues(horizontal = 22.dp, vertical = 16.dp)
    val sizedModifier = modifier.defaultMinSize(minHeight = 58.dp)
    val hapticClick = rememberHapticClick(onClick = onClick)
    when (style) {
        DashcamButtonStyle.Primary -> Button(hapticClick, sizedModifier, enabled = enabled, contentPadding = padding, content = content)
        DashcamButtonStyle.Tonal -> FilledTonalButton(hapticClick, sizedModifier, enabled = enabled, contentPadding = padding, content = content)
        DashcamButtonStyle.Outline -> OutlinedButton(hapticClick, sizedModifier, enabled = enabled, contentPadding = padding, content = content)
    }
}
