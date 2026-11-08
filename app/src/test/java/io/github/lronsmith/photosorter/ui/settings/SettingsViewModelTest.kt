package io.github.lronsmith.photosorter.ui.settings

import io.github.lronsmith.photosorter.decisions.DecisionSource
import io.github.lronsmith.photosorter.decisions.DecisionState
import io.github.lronsmith.photosorter.fakes.FakeDecisionStore
import io.github.lronsmith.photosorter.fakes.FakeTrasher
import io.github.lronsmith.photosorter.media.AccessLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val decisions = FakeDecisionStore()
    private val trasher = FakeTrasher()
    private var access = AccessLevel.FULL

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(sdk: Int = 34) =
        SettingsViewModel(decisions, trasher, { access }, sdkInt = sdk, versionLabel = "1.0.0 (build 1)")

    @Test fun reflectsPermissionsAndCounts() = runTest {
        trasher.instant = true
        decisions.set(listOf(1L, 2L), DecisionState.TRASHED, DecisionSource.SWIPE)
        val vm = viewModel(sdk = 34)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(AccessLevel.FULL, state.access)
        assertTrue(state.manageMediaSupported)
        assertTrue(state.instantTrash)
        assertEquals(2, state.trashedCount)
        assertEquals("1.0.0 (build 1)", state.versionLabel)
    }

    @Test fun api30HidesManageMedia() = runTest {
        val vm = viewModel(sdk = 30)
        advanceUntilIdle()

        assertFalse(vm.state.value.manageMediaSupported)
    }

    @Test fun refreshPicksUpPermissionChange() = runTest {
        trasher.instant = false
        access = AccessLevel.PARTIAL
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.state.value.instantTrash)
        assertEquals(AccessLevel.PARTIAL, vm.state.value.access)

        trasher.instant = true
        access = AccessLevel.FULL
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.state.value.instantTrash)
        assertEquals(AccessLevel.FULL, vm.state.value.access)
    }

    @Test fun trashedCountFollowsStore() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.trashedCount)

        decisions.set(listOf(5L), DecisionState.TRASHED, DecisionSource.DUPLICATE)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.trashedCount)
    }

    @Test fun resetKeepsTrashed() = runTest {
        decisions.set(listOf(1L), DecisionState.KEPT, DecisionSource.SWIPE)
        decisions.set(listOf(2L), DecisionState.PENDING_TRASH, DecisionSource.SWIPE)
        decisions.set(listOf(3L), DecisionState.TRASHED, DecisionSource.SWIPE)
        val vm = viewModel()
        advanceUntilIdle()

        vm.resetDecisions()
        advanceUntilIdle()

        assertEquals(setOf(3L), decisions.decidedIds())
        assertEquals(1, vm.state.value.trashedCount)
    }
}
