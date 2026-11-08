package io.github.lronsmith.photosorter.ui.swipe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lronsmith.photosorter.ui.swipe.SwipeDirection.LEFT
import io.github.lronsmith.photosorter.ui.swipe.SwipeDirection.RIGHT
import io.github.lronsmith.photosorter.ui.swipe.SwipeDirection.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeCardTest {
    @get:Rule val rule = createComposeRule()

    private val got = mutableListOf<SwipeDirection>()
    private val progress = mutableListOf<SwipeProgress>()
    private var key by mutableIntStateOf(1)

    @Before fun setUp() {
        rule.setContent {
            SwipeCard(
                key = key,
                onSwiped = { got += it; key++ },
                onDrag = { progress += it },
            ) { Box(Modifier.fillMaxSize()) }
        }
    }

    private fun card() = rule.onNodeWithTag("swipe-card")

    @Test fun swipeRightKeeps() {
        card().performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(listOf(RIGHT), got)
    }

    @Test fun swipeLeftTrashes() {
        card().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf(LEFT), got)
    }

    @Test fun swipeUpDefers() {
        card().performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(listOf(UP), got)
    }

    @Test fun shortDragSpringsBack() {
        card().performTouchInput { down(center); moveBy(Offset(width * 0.1f, 0f)); up() }
        rule.waitForIdle()
        assertTrue(got.isEmpty())
        assertTrue("drag was reported", progress.any { it.x > 0f })
        assertEquals(SwipeProgress(0f, 0f), progress.last())
    }

    @Test fun nextCardResetsAndSwipesAgain() {
        card().performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(SwipeProgress(0f, 0f), progress.last())
        card().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf(RIGHT, LEFT), got)
    }

    @Test fun keyChangeDuringFlyOffCancelsIt() {
        rule.mainClock.autoAdvance = false
        card().performTouchInput { swipeRight() }
        rule.mainClock.advanceTimeByFrame()
        key = 99 // the item was replaced (e.g. dropped) before the fly-off finished
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue("new item must not be swiped", got.isEmpty())
        assertEquals(SwipeProgress(0f, 0f), progress.last())
    }
}
