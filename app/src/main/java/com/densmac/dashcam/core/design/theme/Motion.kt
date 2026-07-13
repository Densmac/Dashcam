package com.densmac.dashcam.core.design.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

object DashcamMotion {
    val expressiveEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> standard(durationMillis: Int = 280): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = expressiveEase)
}
