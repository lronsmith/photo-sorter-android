package io.github.lronsmith.photosorter.ui.swipe

import kotlin.math.abs
import kotlin.math.sign

enum class SwipeDirection { LEFT, RIGHT, UP }

/** Drag as a fraction of the card's width ([x]) and height ([y]), each in −1..1; `(0, 0)` at rest. */
data class SwipeProgress(val x: Float, val y: Float)

/** Horizontal drag past this fraction of the width keeps / trashes. */
internal const val HORIZONTAL_THRESHOLD = 0.30f

/** Upward drag past this fraction of the height defers ("later"). */
internal const val UP_THRESHOLD = 0.20f

/**
 * Decides what a released drag of ([dx], [dy]) px with velocity ([vx], [vy]) px/s means on a
 * card of [width] × [height] px, or `null` to spring back. The dominant axis (relative to the
 * card's size) decides; a fling only counts in the direction the card was dragged.
 */
fun resolveSwipe(
    dx: Float,
    dy: Float,
    vx: Float,
    vy: Float,
    width: Float,
    height: Float,
    flingVelocity: Float,
): SwipeDirection? {
    if (width <= 0f || height <= 0f) return null
    return if (abs(dx) / width >= abs(dy) / height) {
        val past = abs(dx) > HORIZONTAL_THRESHOLD * width
        val fling = abs(vx) > flingVelocity && sign(vx) == sign(dx)
        when {
            dx == 0f || !(past || fling) -> null
            dx > 0f -> SwipeDirection.RIGHT
            else -> SwipeDirection.LEFT
        }
    } else {
        val past = -dy > UP_THRESHOLD * height
        val fling = -vy > flingVelocity && dy < 0f
        if (past || fling) SwipeDirection.UP else null
    }
}

/**
 * Alpha of an edge glow given the drag toward its side as a fraction of the width
 * (negative = away): 0.25 at rest, rising linearly to 1.0 at the keep/trash threshold.
 */
internal fun edgeGlowAlpha(towardFraction: Float): Float =
    0.25f + 0.75f * (towardFraction / HORIZONTAL_THRESHOLD).coerceIn(0f, 1f)
