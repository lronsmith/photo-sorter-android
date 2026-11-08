package io.github.lronsmith.photosorter.ui.swipe

import io.github.lronsmith.photosorter.ui.swipe.SwipeDirection.LEFT
import io.github.lronsmith.photosorter.ui.swipe.SwipeDirection.RIGHT
import io.github.lronsmith.photosorter.ui.swipe.SwipeDirection.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Width 1000, height 2000, fling velocity 2000.
class SwipeGestureTest {
    @Test fun rightPastThreshold() = assertEquals(RIGHT, resolveSwipe(310f, 0f, 0f, 0f, 1000f, 2000f, 2000f))

    @Test fun leftPastThreshold() = assertEquals(LEFT, resolveSwipe(-310f, 20f, 0f, 0f, 1000f, 2000f, 2000f))

    @Test fun upPastThreshold() = assertEquals(UP, resolveSwipe(30f, -410f, 0f, 0f, 1000f, 2000f, 2000f))

    @Test fun shortDragReturnsNull() = assertNull(resolveSwipe(200f, -100f, 0f, 0f, 1000f, 2000f, 2000f))

    @Test fun flingCounts() = assertEquals(LEFT, resolveSwipe(-50f, 0f, -2500f, 0f, 1000f, 2000f, 2000f))

    @Test fun flingAgainstDragIgnored() = assertNull(resolveSwipe(50f, 0f, -2500f, 0f, 1000f, 2000f, 2000f))

    @Test fun downwardIgnored() = assertNull(resolveSwipe(0f, 900f, 0f, 3000f, 1000f, 2000f, 2000f))

    @Test fun upwardFlingCounts() = assertEquals(UP, resolveSwipe(0f, -50f, 0f, -2500f, 1000f, 2000f, 2000f))

    @Test fun noMovementReturnsNull() = assertNull(resolveSwipe(0f, 0f, 3000f, -3000f, 1000f, 2000f, 2000f))

    @Test fun glowRestsAtQuarterAlpha() = assertEquals(0.25f, edgeGlowAlpha(0f), 1e-6f)

    @Test fun glowRisesLinearlyToFullAtThreshold() {
        assertEquals(0.625f, edgeGlowAlpha(0.15f), 1e-6f)
        assertEquals(1f, edgeGlowAlpha(0.30f), 1e-6f)
        assertEquals(1f, edgeGlowAlpha(0.9f), 1e-6f)
    }

    @Test fun glowIgnoresDragAwayFromItsSide() = assertEquals(0.25f, edgeGlowAlpha(-0.5f), 1e-6f)
}
