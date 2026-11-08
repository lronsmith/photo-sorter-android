package io.github.lronsmith.photosorter.ui.swipe

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lronsmith.photosorter.decisions.DecisionSource
import io.github.lronsmith.photosorter.decisions.DecisionSource.SWIPE
import io.github.lronsmith.photosorter.decisions.DecisionState
import io.github.lronsmith.photosorter.decisions.DecisionState.KEPT
import io.github.lronsmith.photosorter.decisions.DecisionState.PENDING_TRASH
import io.github.lronsmith.photosorter.decisions.DecisionState.TRASHED
import io.github.lronsmith.photosorter.decisions.DecisionStore
import io.github.lronsmith.photosorter.fakes.FakeDecisionStore
import io.github.lronsmith.photosorter.fakes.FakeMediaSource
import io.github.lronsmith.photosorter.fakes.FakeSettingsStore
import io.github.lronsmith.photosorter.fakes.FakeTrasher
import io.github.lronsmith.photosorter.fakes.testItem
import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.media.TrashedItem
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.trash.RequestLauncher
import io.github.lronsmith.photosorter.ui.swipe.SwipeDirection.LEFT
import io.github.lronsmith.photosorter.ui.swipe.SwipeDirection.RIGHT
import io.github.lronsmith.photosorter.ui.swipe.SwipeDirection.UP
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModelTest {
    private val items = (1L..5).map { testItem(it) }
    private val media = FakeMediaSource(items)
    private val decisions = FakeDecisionStore()
    private val settings = FakeSettingsStore()
    private val trasher = FakeTrasher()
    private val ok = RequestLauncher { true }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    /** Stands in for the application scope: outlives the ViewModel, runs on the test scheduler. */
    private var workerScope: CoroutineScope? = null

    @After fun tearDown() {
        workerScope?.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.worker(): CoroutineScope =
        workerScope ?: CoroutineScope(StandardTestDispatcher(testScheduler) + Job()).also { workerScope = it }

    private fun TestScope.loaded(store: DecisionStore = decisions): SwipeViewModel =
        SwipeViewModel(media, store, settings, trasher, worker()).also { advanceUntilIdle() }

    /** Clears [vm] the way Android does when its screen closes. */
    private fun clear(vm: SwipeViewModel) {
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { vm } })[SwipeViewModel::class]
        store.clear()
    }

    private val SwipeViewModel.ui: SwipeUiState get() = state.value

    private fun SwipeViewModel.queueIds(): List<Long?> = listOf(ui.current?.id) + ui.upcoming.map { it.id }

    private fun states(): Map<Long, DecisionState> = decisions.rows.mapValues { it.value.state }

    @Test fun loadsUndecidedItemsInOrderFromIncludedAlbums() = runTest {
        media.items = items.map { if (it.id == 4L) testItem(4, albumId = "wa", albumName = "WhatsApp") else it }
        settings.excludedAlbumIds.value = setOf("wa")
        decisions.set(listOf(2L), KEPT, SWIPE)
        val vm = loaded()
        assertFalse(vm.ui.loading)
        assertEquals(listOf<Long?>(1, 3, 5), vm.queueIds())
        assertEquals(3, vm.ui.total)
        assertEquals(0, vm.ui.decided)
    }

    @Test fun rightKeeps() = runTest {
        val vm = loaded()
        vm.onSwipe(RIGHT, ok); advanceUntilIdle()
        assertEquals(KEPT, decisions.rows.getValue(1).state)
        assertEquals(2L, vm.ui.current?.id)
        assertEquals(1, vm.ui.decided)
        assertEquals(1, vm.ui.sessionKept)
        assertTrue(vm.ui.canUndo)
    }

    @Test fun upDefersWithoutPersisting() = runTest {
        val vm = loaded()
        vm.onSwipe(UP, ok); advanceUntilIdle()
        assertTrue(decisions.rows.isEmpty())
        assertEquals(2L, vm.ui.current?.id)
        assertEquals(0, vm.ui.decided)
        repeat(4) { vm.onSwipe(RIGHT, ok) } // keep 2..5: item 1 was moved to the back
        assertEquals(1L, vm.ui.current?.id)
    }

    @Test fun leftBatchModeQueuesPending() = runTest {
        val vm = loaded()
        vm.onSwipe(LEFT, ok); advanceUntilIdle()
        assertEquals(PENDING_TRASH, decisions.rows.getValue(1).state)
        assertEquals(1, vm.ui.pendingCount)
        assertTrue(trasher.trashCalls.isEmpty())
        assertEquals(1, vm.ui.sessionTrashed)
    }

    @Test fun leftInstantTrashes() = runTest {
        trasher.instant = true
        val vm = loaded()
        vm.onSwipe(LEFT, ok); advanceUntilIdle()
        assertEquals(listOf(listOf(1L)), trasher.trashCalls)
        assertEquals(TRASHED, decisions.rows.getValue(1).state)
        assertEquals(0, vm.ui.pendingCount)
        assertEquals(1, vm.ui.sessionTrashed)
    }

    @Test fun instantFailureFallsBackToPending() = runTest {
        trasher.instant = true
        trasher.trashResult = { emptyList() }
        val vm = loaded()
        vm.onSwipe(LEFT, ok); advanceUntilIdle()
        assertEquals(PENDING_TRASH, decisions.rows.getValue(1).state)
        assertEquals(1, vm.ui.pendingCount)
        assertEquals("Couldn't trash that item. It's waiting in the batch.", vm.ui.message)
    }

    @Test fun rapidInstantTrashesAreSerializedAndAllRecorded() = runTest {
        trasher.instant = true
        val gate = CompletableDeferred<Unit>()
        trasher.gate = gate
        val vm = loaded()
        repeat(5) { vm.onSwipe(LEFT, ok) }
        advanceUntilIdle()
        assertEquals(listOf(listOf(1L)), trasher.trashCalls) // the others wait their turn
        assertTrue(vm.ui.done)

        gate.complete(Unit); advanceUntilIdle()
        assertEquals((1L..5).map { listOf(it) }, trasher.trashCalls)
        assertEquals((1L..5).associateWith { TRASHED }, states())
        assertEquals(5, vm.ui.sessionTrashed)

        vm.onUndo(ok); advanceUntilIdle()
        assertEquals(listOf(listOf(5L)), trasher.restoreCalls)
        assertEquals((1L..4).associateWith { TRASHED }, states())
        assertEquals(5L, vm.ui.current?.id)
        assertEquals(4, vm.ui.sessionTrashed)
    }

    @Test fun undoWaitsForInFlightInstantTrashThenRestores() = runTest {
        trasher.instant = true
        val gate = CompletableDeferred<Unit>()
        trasher.gate = gate
        val vm = loaded()
        vm.onSwipe(LEFT, ok); advanceUntilIdle()
        vm.onUndo(ok)
        vm.onSwipe(RIGHT, ok) // ignored while the undo is in progress
        advanceUntilIdle()
        assertTrue(trasher.restoreCalls.isEmpty())
        assertEquals(2L, vm.ui.current?.id)
        assertFalse(vm.ui.canUndo)

        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(listOf(1L)), trasher.restoreCalls)
        assertTrue(decisions.rows.isEmpty())
        assertEquals(1L, vm.ui.current?.id)
        assertFalse(vm.ui.canUndo)
        assertEquals(0, vm.ui.sessionTrashed)
    }

    @Test fun undoEachKind() = runTest {
        val vm = loaded()

        vm.onSwipe(RIGHT, ok); advanceUntilIdle()
        vm.onUndo(ok); advanceUntilIdle()
        assertTrue(decisions.rows.isEmpty())
        assertEquals(1L, vm.ui.current?.id)
        assertEquals(0, vm.ui.sessionKept)

        vm.onSwipe(LEFT, ok); advanceUntilIdle() // batch mode: pending
        vm.onUndo(ok); advanceUntilIdle()
        assertTrue(decisions.rows.isEmpty())
        assertTrue(trasher.restoreCalls.isEmpty())
        assertEquals(1L, vm.ui.current?.id)
        assertEquals(0, vm.ui.pendingCount)

        trasher.instant = true
        vm.onSwipe(LEFT, ok); advanceUntilIdle()
        vm.onUndo(ok); advanceUntilIdle()
        assertEquals(listOf(listOf(1L)), trasher.restoreCalls)
        assertTrue(decisions.rows.isEmpty())
        assertEquals(1L, vm.ui.current?.id)
        assertEquals(0, vm.ui.sessionTrashed)

        vm.onSwipe(UP, ok); advanceUntilIdle()
        vm.onUndo(ok); advanceUntilIdle()
        assertEquals(listOf<Long?>(1, 2, 3), vm.queueIds())
        assertTrue(decisions.rows.isEmpty())
        assertFalse(vm.ui.canUndo)
        assertEquals(0, vm.ui.decided)
    }

    @Test fun undoRestoreFailureKeepsTrashed() = runTest {
        trasher.instant = true
        trasher.restoreResult = { emptyList() }
        val vm = loaded()
        vm.onSwipe(LEFT, ok); advanceUntilIdle()
        vm.onUndo(ok); advanceUntilIdle()
        assertEquals(TRASHED, decisions.rows.getValue(1).state)
        assertEquals("Couldn't restore that item.", vm.ui.message)
        assertEquals(2L, vm.ui.current?.id)
        assertTrue(vm.ui.canUndo)
        assertEquals(1, vm.ui.sessionTrashed)
    }

    @Test fun trashPendingKeepsFailedChunkPending() = runTest {
        val vm = loaded()
        repeat(3) { vm.onSwipe(LEFT, ok) }
        advanceUntilIdle()
        trasher.trashResult = { it.take(2) }
        vm.onTrashPending(ok); advanceUntilIdle()
        assertEquals(listOf(listOf(1L, 2L, 3L)), trasher.trashCalls)
        assertEquals(mapOf(1L to TRASHED, 2L to TRASHED, 3L to PENDING_TRASH), states())
        assertEquals(1, vm.ui.pendingCount)
        assertEquals("Some items weren't trashed. They're still waiting.", vm.ui.message)

        vm.onMessageShown()
        assertNull(vm.ui.message)
    }

    @Test fun undoAfterBatchTrashRestores() = runTest {
        val vm = loaded()
        vm.onSwipe(LEFT, ok)
        vm.onTrashPending(ok); advanceUntilIdle()
        assertEquals(TRASHED, decisions.rows.getValue(1).state)
        vm.onUndo(ok); advanceUntilIdle()
        assertEquals(listOf(listOf(1L)), trasher.restoreCalls)
        assertTrue(decisions.rows.isEmpty())
        assertEquals(1L, vm.ui.current?.id)
    }

    @Test fun loadFailedMissingItemIsDropped() = runTest {
        val vm = loaded()
        media.missing += 1
        vm.onLoadFailed(items[0]); advanceUntilIdle()
        assertEquals(2L, vm.ui.current?.id)
        assertEquals(4, vm.ui.total)
        assertTrue(vm.ui.undecodable.isEmpty())
    }

    @Test fun loadFailedExistingItemIsMarkedUndecodable() = runTest {
        val vm = loaded()
        vm.onLoadFailed(items[0]); advanceUntilIdle()
        assertEquals(setOf(1L), vm.ui.undecodable)
        assertEquals(1L, vm.ui.current?.id)
        assertEquals(5, vm.ui.total)
    }

    @Test fun endOfQueueIsDone() = runTest {
        val vm = loaded()
        listOf(RIGHT, LEFT, RIGHT, LEFT, RIGHT).forEach { vm.onSwipe(it, ok) }
        advanceUntilIdle()
        assertTrue(vm.ui.done)
        assertNull(vm.ui.current)
        assertEquals(5, vm.ui.sessionKept + vm.ui.sessionTrashed)
        assertEquals(5, vm.ui.decided)
        assertEquals(2, vm.ui.pendingCount)
    }

    @Test fun pendingFromEarlierSessionCountsButIsNotQueued() = runTest {
        decisions.set(listOf(3L), PENDING_TRASH, SWIPE)
        val vm = loaded()
        assertEquals(1, vm.ui.pendingCount)
        assertEquals(listOf<Long?>(1, 2, 4), vm.queueIds())
        assertEquals(4, vm.ui.total)
    }

    @Test fun instantFlagIsReadOnEverySwipe() = runTest {
        trasher.instant = true
        val vm = loaded()
        vm.onSwipe(LEFT, ok); advanceUntilIdle()
        assertEquals(TRASHED, decisions.rows.getValue(1).state)
        trasher.instant = false // Media management revoked mid-session
        vm.onSwipe(LEFT, ok); advanceUntilIdle()
        assertEquals(PENDING_TRASH, decisions.rows.getValue(2).state)
        assertEquals(listOf(listOf(1L)), trasher.trashCalls)
    }

    @Test fun queuedDecisionsAreWrittenAfterTheViewModelIsCleared() = runTest {
        trasher.instant = true
        val gate = CompletableDeferred<Unit>()
        trasher.gate = gate
        val vm = loaded()
        vm.onSwipe(LEFT, ok) // instant trash of 1, held at the gate
        trasher.instant = false
        vm.onSwipe(RIGHT, ok) // KEPT 2, queued
        vm.onSwipe(LEFT, ok) // PENDING_TRASH 3, queued
        advanceUntilIdle()
        clear(vm) // back pressed: the launcher now fails, but the system already trashed 1
        trasher.trashResult = { emptyList() }
        media.trashed[1] = TrashedItem(items[0], expiresAt = 0)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(mapOf(1L to TRASHED, 2L to KEPT, 3L to PENDING_TRASH), states())
    }

    @Test fun failedInstantTrashFoundInSystemTrashIsRecordedTrashed() = runTest {
        trasher.instant = true
        trasher.trashResult = { emptyList() }
        media.trashed[1] = TrashedItem(items[0], expiresAt = 0)
        val vm = loaded()
        vm.onSwipe(LEFT, ok); advanceUntilIdle()
        assertEquals(TRASHED, decisions.rows.getValue(1).state)
        assertNull(vm.ui.message)
        vm.onUndo(ok); advanceUntilIdle() // it really is in the trash, so undo restores it
        assertEquals(listOf(listOf(1L)), trasher.restoreCalls)
    }

    @Test fun failedBatchChunkFoundInSystemTrashIsRecordedTrashed() = runTest {
        val vm = loaded()
        repeat(2) { vm.onSwipe(LEFT, ok) }
        advanceUntilIdle()
        trasher.trashResult = { emptyList() }
        media.trashed[2] = TrashedItem(items[1], expiresAt = 0)
        vm.onTrashPending(ok); advanceUntilIdle()
        assertEquals(mapOf(1L to PENDING_TRASH, 2L to TRASHED), states())
        assertEquals(BATCH_FAILED_MESSAGE, vm.ui.message)
    }

    @Test fun pendingOutsideTheLibraryIsNotCountedOrTrashed() = runTest {
        decisions.set(listOf(3L, 99L), PENDING_TRASH, SWIPE) // 99: deleted elsewhere / not visible
        val vm = loaded()
        assertEquals(1, vm.ui.pendingCount)
        vm.onTrashPending(ok); advanceUntilIdle()
        assertEquals(listOf(listOf(3L)), trasher.trashCalls)
        assertEquals(mapOf(3L to TRASHED, 99L to PENDING_TRASH), states())
        assertEquals(0, vm.ui.pendingCount)
        assertNull(vm.ui.message)
    }

    @Test fun loadFailureOfAnItemSwipedMeanwhileDoesNotDropIt() = runTest {
        trasher.instant = true
        val vm = loaded()
        vm.onLoadFailed(items[0]) // exists() not answered yet...
        vm.onSwipe(LEFT, ok) // ...when 1 is trashed: it now reads as gone
        media.missing += 1
        advanceUntilIdle()
        assertEquals(5, vm.ui.total)
        assertEquals(1, vm.ui.decided)
        media.missing -= 1
        vm.onUndo(ok); advanceUntilIdle()
        assertEquals(listOf(listOf(1L)), trasher.restoreCalls)
        assertTrue(decisions.rows.isEmpty())
        assertEquals(1L, vm.ui.current?.id)
        assertEquals(0, vm.ui.decided)
    }

    @Test fun failingJobShowsMessageAndWorkerKeepsRunning() = runTest {
        val store = FailingSetStore(decisions)
        val vm = loaded(store)
        store.fail = true
        vm.onSwipe(RIGHT, ok); advanceUntilIdle()
        assertEquals("Something went wrong. Please try again.", vm.ui.message)
        store.fail = false
        vm.onSwipe(RIGHT, ok); advanceUntilIdle()
        assertEquals(mapOf(2L to KEPT), states())
    }

    @Test fun failingLoadShowsMessage() = runTest {
        val vm = SwipeViewModel(FailingMediaSource, decisions, settings, trasher, worker())
        advanceUntilIdle()
        assertEquals("Something went wrong. Please try again.", vm.ui.message)
        vm.onTrashPending(ok); advanceUntilIdle() // the worker is still alive
        assertTrue(trasher.trashCalls.isEmpty())
    }

    /** [DecisionStore] whose [set] throws while [fail] is true. */
    private class FailingSetStore(private val inner: FakeDecisionStore) : DecisionStore by inner {
        var fail = false

        override suspend fun set(mediaIds: Collection<Long>, state: DecisionState, source: DecisionSource) {
            if (fail) throw IOException("disk full")
            inner.set(mediaIds, state, source)
        }
    }

    private object FailingMediaSource : MediaSource by FakeMediaSource() {
        override suspend fun allItems(): List<MediaItem> = throw IOException("query failed")
    }
}
