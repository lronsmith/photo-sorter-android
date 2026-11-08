package io.github.lronsmith.photosorter.ui.swipe

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Degrees of tilt per full card width of horizontal drag. */
private const val ROTATION_PER_WIDTH = 12f

/** How far (in card widths / heights) a swiped card travels so it is fully off-screen, tilt included. */
private const val FLY_OFF_WIDTHS = 1.6f
private const val FLY_OFF_HEIGHTS = 1.3f
private const val FLY_OFF_MILLIS = 180

/**
 * A full-screen card that follows the finger. On release past a threshold (see [resolveSwipe])
 * it flies off-screen and then calls [onSwiped]; otherwise it springs back. [onDrag] receives
 * every movement (drag and animation) and `SwipeProgress(0f, 0f)` when the card is back at rest.
 *
 * After a swipe the card stays off-screen until [key] changes (the caller shows the next item),
 * which resets it to the centre.
 */
@Composable
fun SwipeCard(
    key: Any,
    onSwiped: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    onDrag: (SwipeProgress) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val currentOnSwiped by rememberUpdatedState(onSwiped)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val scope = rememberCoroutineScope()
    val flingVelocity = with(LocalDensity.current) { 800.dp.toPx() }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember(key) { mutableStateOf(Offset.Zero) }
    // True from release past a threshold until the next key: further input is ignored.
    var swiped by remember(key) { mutableStateOf(false) }
    var settle by remember(key) { mutableStateOf<Job?>(null) }

    fun moveTo(value: Offset) {
        offset = value
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        currentOnDrag(
            SwipeProgress(
                x = if (w > 0f) (value.x / w).coerceIn(-1f, 1f) else 0f,
                y = if (h > 0f) (value.y / h).coerceIn(-1f, 1f) else 0f,
            ),
        )
    }

    // A new key means a new item: it starts at rest, and the old card's animation must not
    // finish (it would report the old drag and swipe the new item).
    DisposableEffect(key) {
        currentOnDrag(SwipeProgress(0f, 0f))
        onDispose { settle?.cancel() }
    }

    Box(
        modifier
            .testTag("swipe-card")
            .onSizeChanged { size = it }
            // Before graphicsLayer, so touch coordinates don't move with the card.
            .pointerInput(key, flingVelocity) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = {
                        if (!swiped) {
                            settle?.cancel()
                            tracker.resetTracking()
                        }
                    },
                    onDrag = { change, amount ->
                        if (!swiped) {
                            change.consume()
                            tracker.addPointerInputChange(change)
                            moveTo(offset + amount)
                        }
                    },
                    onDragEnd = {
                        if (!swiped) {
                            val velocity = tracker.calculateVelocity()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val direction = resolveSwipe(
                                offset.x, offset.y, velocity.x, velocity.y, w, h, flingVelocity,
                            )
                            if (direction == null) {
                                settle = scope.launch { animateTo(offset, Offset.Zero, ::moveTo) }
                            } else {
                                swiped = true
                                val target = when (direction) {
                                    SwipeDirection.RIGHT -> Offset(w * FLY_OFF_WIDTHS, offset.y)
                                    SwipeDirection.LEFT -> Offset(-w * FLY_OFF_WIDTHS, offset.y)
                                    SwipeDirection.UP -> Offset(offset.x, -h * FLY_OFF_HEIGHTS)
                                }
                                settle = scope.launch {
                                    flyTo(offset, target, ::moveTo)
                                    currentOnSwiped(direction)
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        if (!swiped) settle = scope.launch { animateTo(offset, Offset.Zero, ::moveTo) }
                    },
                )
            }
            .graphicsLayer {
                translationX = offset.x
                translationY = offset.y
                rotationZ = if (size.width > 0) offset.x / size.width * ROTATION_PER_WIDTH else 0f
            },
    ) {
        content()
    }
}

/** Springs back to [target]. */
private suspend fun animateTo(from: Offset, target: Offset, onValue: (Offset) -> Unit) {
    animate(
        typeConverter = Offset.VectorConverter,
        initialValue = from,
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
    ) { value, _ -> onValue(value) }
}

/** Short accelerating slide off-screen. */
private suspend fun flyTo(from: Offset, target: Offset, onValue: (Offset) -> Unit) {
    animate(
        typeConverter = Offset.VectorConverter,
        initialValue = from,
        targetValue = target,
        animationSpec = tween(durationMillis = FLY_OFF_MILLIS, easing = FastOutLinearInEasing),
    ) { value, _ -> onValue(value) }
}
