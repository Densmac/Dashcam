package com.densmac.dashcam.core.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.densmac.dashcam.core.design.theme.HeroShape

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(if (hero) 24.dp else 20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "card-scale")
    val shape = if (hero) HeroShape else MaterialTheme.shapes.large
    val panelBrush = Brush.linearGradient(
        colors = if (hero) {
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
            )
        } else {
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            )
        }
    )
    var cardModifier = modifier
        .scale(scale)
        .shadow(if (hero) 22.dp else 10.dp, shape = shape, clip = false)
        .clip(shape)
        .background(panelBrush)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
    if (onClick != null) {
        cardModifier = cardModifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
    }
    Column(cardModifier.padding(contentPadding), content = content)
}
