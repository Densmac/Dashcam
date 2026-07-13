package com.densmac.dashcam.core.design.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import javax.inject.Inject

enum class HapticEvent {
    Tick,
    Confirmation,
    Warning,
    Error
}

class HapticFeedbackManager @Inject constructor()

@Composable
fun rememberDashcamHaptics(enabled: Boolean): (HapticEvent) -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(enabled, haptics) {
        { event ->
            if (enabled) {
                val type = when (event) {
                    HapticEvent.Tick -> HapticFeedbackType.TextHandleMove
                    HapticEvent.Confirmation -> HapticFeedbackType.LongPress
                    HapticEvent.Warning -> HapticFeedbackType.LongPress
                    HapticEvent.Error -> HapticFeedbackType.LongPress
                }
                haptics.performHapticFeedback(type)
            }
        }
    }
}
