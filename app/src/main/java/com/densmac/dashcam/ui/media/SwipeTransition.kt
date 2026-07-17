package com.densmac.dashcam.ui.media

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Drives a dramatic prev/next clip transition: the current clip zooms and slides out toward the
 * swipe direction, the clip is switched at the low point, then the new clip zooms back to full size
 * from the opposite side. Works because the media is rendered on a TextureView (which, unlike a
 * SurfaceView, can be scaled/translated by a [graphicsLayer]).
 */
class SwipeZoomController(
    private val scope: CoroutineScope,
    val scale: Animatable<Float, AnimationVector1D>,
    val offset: Animatable<Float, AnimationVector1D>,
    val alpha: Animatable<Float, AnimationVector1D>
) {
    /**
     * @param direction +1 for next (slide out left), -1 for previous (slide out right).
     * @param onSwitch invoked at the zoomed-out low point to advance to the adjacent clip.
     */
    fun animate(direction: Int, onSwitch: () -> Unit) {
        scope.launch {
            // Zoom + slide the current clip out toward the swipe direction.
            coroutineScope {
                launch { scale.animateTo(ZOOM_OUT, tween(190, easing = FastOutSlowInEasing)) }
                launch { alpha.animateTo(0.3f, tween(190)) }
                launch { offset.animateTo(-SLIDE * direction, tween(190, easing = FastOutSlowInEasing)) }
            }
            onSwitch()
            // New clip enters from the opposite side and zooms back to full size.
            offset.snapTo(SLIDE * direction)
            coroutineScope {
                launch { scale.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
                launch { alpha.animateTo(1f, tween(260)) }
                launch { offset.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
            }
        }
    }

    private companion object {
        const val ZOOM_OUT = 0.72f
        const val SLIDE = 0.22f // fraction of width
    }
}

@Composable
fun rememberSwipeZoom(): SwipeZoomController {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    return remember(scope) { SwipeZoomController(scope, scale, offset, alpha) }
}

/** Apply the running zoom transform to the media container. */
fun Modifier.swipeZoom(zoom: SwipeZoomController): Modifier = graphicsLayer {
    scaleX = zoom.scale.value
    scaleY = zoom.scale.value
    translationX = zoom.offset.value * size.width
    alpha = zoom.alpha.value
}
