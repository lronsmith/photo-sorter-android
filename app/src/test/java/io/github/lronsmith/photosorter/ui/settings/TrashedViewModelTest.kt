package io.github.lronsmith.photosorter.ui.settings

import io.github.lronsmith.photosorter.decisions.DecisionSource
import io.github.lronsmith.photosorter.decisions.DecisionState
import io.github.lronsmith.photosorter.fakes.FakeDecisionStore
import io.github.lronsmith.photosorter.fakes.FakeMediaSource
import io.github.lronsmith.photosorter.fakes.FakeTrasher
import io.github.lronsmith.photosorter.fakes.testItem
import io.github.lronsmith.photosorter.media.TrashedItem
import io.github.lronsmith.photosorter.trash.RequestLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashedViewModelTest {
    private val media = FakeMediaSource()
    private val decisions = FakeDecisionStore()
    private val trasher = FakeTrasher()
    private val ok = RequestLauncher { true }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** Marks each id TRASHED in the store and puts it in the media trash with the given expiry. */
    private suspend fun trashed(vararg expiries: Pair<Long, Long>) {
        decisions.set(expiries.map { it.first }, DecisionState.TRASHED, DecisionSource.SWIPE)
        expiries.forEach { (id, expiresAt) -> media.trashed[id] = TrashedItem(testItem(id), expiresAt) }
    }

    private fun TestScope.loaded(): TrashedViewModel {
        val vm = TrashedViewModel(media, decisions, trasher)
        vm.load()
        advanceUntilIdle()
        return vm
    }

    private fun TrashedViewModel.itemIds() = state.value.items.map { it.item.id }

    @Test fun startsLoading() {
        assertTrue(TrashedViewModel(media, decisions, trasher).state.value.loading)
    }

    @Test fun loadsTrashedSortedByExpiry() = runTest {
        trashed(1L to 900L, 2L to 100L)

        val vm = loaded()

        assertFalse(vm.state.value.loading)
        assertEquals(listOf(2L, 1L), vm.itemIds())
    }

    @Test fun omitsRowsNoLongerInTrash() = runTest {
        trashed(1L to 900L, 2L to 100L)
        media.trashed.remove(2L)

        val vm = loaded()

        assertEquals(listOf(1L), vm.itemIds())
    }

    @Test fun restoreSelectedClearsRows() = runTest {
        trashed(1L to 100L, 2L to 900L)
        val vm = loaded()

        vm.toggle(1L)
        vm.restoreSelected(ok)
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L)), trasher.restoreCalls)
        assertEquals(setOf(2L), decisions.decidedIds())
        assertEquals(listOf(2L), vm.itemIds())
        assertEquals(emptySet<Long>(), vm.state.value.selected)
        assertFalse(vm.state.value.restoring)
        assertNull(vm.state.value.message)
    }

    @Test fun restoreAllPartialFailure() = runTest {
        trashed(1L to 100L, 2L to 900L)
        trasher.restoreResult = { it.take(1) }
        val vm = loaded()

        vm.restoreAll(ok)
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L, 2L)), trasher.restoreCalls)
        assertEquals(setOf(2L), decisions.decidedIds())
        assertEquals(listOf(2L), vm.itemIds())
        assertFalse(vm.state.value.restoring)
        assertEquals("Some items couldn't be restored.", vm.state.value.message)

        vm.onMessageShown()
        assertNull(vm.state.value.message)
    }

    @Test fun selectAllAndToggle() = runTest {
        trashed(1L to 100L, 2L to 200L, 3L to 300L)
        val vm = loaded()

        vm.toggle(2L)
        assertEquals(setOf(2L), vm.state.value.selected)
        vm.toggle(2L)
        assertEquals(emptySet<Long>(), vm.state.value.selected)

        vm.selectAll()
        assertEquals(setOf(1L, 2L, 3L), vm.state.value.selected)
        vm.toggle(3L)
        assertEquals(setOf(1L, 2L), vm.state.value.selected)
    }

    @Test fun reloadDropsSelectionOfVanishedItems() = runTest {
        trashed(1L to 100L, 2L to 200L)
        val vm = loaded()
        vm.selectAll()

        media.trashed.remove(1L)
        vm.load()
        advanceUntilIdle()

        assertEquals(listOf(2L), vm.itemIds())
        assertEquals(setOf(2L), vm.state.value.selected)
    }
}
