package io.github.lronsmith.photosorter.ui.swipe

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The video player plays only while the screen is started (not after Home / app switch). */
@RunWith(AndroidJUnit4::class)
class MediaCardLifecycleTest {
    private val calls = mutableListOf<Boolean>()

    private fun lifecycleAt(state: Lifecycle.State): LifecycleRegistry {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        return owner.registry.apply { currentState = state }
    }

    @Test fun playsWhenAddedToStartedScreen() {
        val lifecycle = lifecycleAt(Lifecycle.State.RESUMED)
        lifecycle.addObserver(playWhileStarted { calls += it })
        assertEquals(listOf(true), calls)
    }

    @Test fun pausesOnStopAndResumesOnStart() {
        val lifecycle = lifecycleAt(Lifecycle.State.RESUMED)
        lifecycle.addObserver(playWhileStarted { calls += it })
        lifecycle.currentState = Lifecycle.State.CREATED // Home pressed
        lifecycle.currentState = Lifecycle.State.RESUMED // back to the app
        assertEquals(listOf(true, false, true), calls)
    }

    @Test fun doesNotStartWhileScreenIsStopped() {
        val lifecycle = lifecycleAt(Lifecycle.State.CREATED)
        lifecycle.addObserver(playWhileStarted { calls += it })
        assertEquals(emptyList<Boolean>(), calls)
        lifecycle.currentState = Lifecycle.State.STARTED
        assertEquals(listOf(true), calls)
    }

    @Test fun removedObserverNoLongerControlsPlayback() {
        val lifecycle = lifecycleAt(Lifecycle.State.RESUMED)
        val observer = playWhileStarted { calls += it }
        lifecycle.addObserver(observer)
        lifecycle.removeObserver(observer)
        lifecycle.currentState = Lifecycle.State.CREATED
        assertEquals(listOf(true), calls)
    }
}
