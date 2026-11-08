package io.github.lronsmith.photosorter.fakes

import io.github.lronsmith.photosorter.decisions.DecisionCounts
import io.github.lronsmith.photosorter.decisions.DecisionSource.DUPLICATE
import io.github.lronsmith.photosorter.decisions.DecisionSource.SWIPE
import io.github.lronsmith.photosorter.decisions.DecisionState.KEPT
import io.github.lronsmith.photosorter.decisions.DecisionState.PENDING_TRASH
import io.github.lronsmith.photosorter.decisions.DecisionState.TRASHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as RoomDecisionStoreTest: later ViewModel tests rely on the fake matching Room. */
class FakeDecisionStoreTest {
    private val store = FakeDecisionStore(clock = { 123L })

    @Test fun setUpsertsAndRecordsSourceAndTime() = runTest {
        store.set(listOf(1), PENDING_TRASH, SWIPE); store.set(listOf(1), TRASHED, DUPLICATE)
        assertEquals(listOf(1L), store.idsIn(TRASHED)); assertTrue(store.idsIn(PENDING_TRASH).isEmpty())
        assertEquals(DUPLICATE, store.rows.getValue(1).source); assertEquals(123L, store.rows.getValue(1).decidedAt)
    }

    @Test fun idsInIsAscending() = runTest {
        store.set(listOf(3, 1, 2), KEPT, SWIPE)
        assertEquals(listOf(1L, 2L, 3L), store.idsIn(KEPT))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun countsFlowEmitsOnChange() = runTest {
        val seen = mutableListOf<DecisionCounts>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.observeCounts().toList(seen) }
        store.set(listOf(1), KEPT, SWIPE); store.set(listOf(2, 3), PENDING_TRASH, SWIPE); store.set(listOf(4), TRASHED, DUPLICATE)
        store.set(listOf(4), TRASHED, DUPLICATE) // no change in counts: no emission
        assertEquals(
            listOf(DecisionCounts(0, 0, 0), DecisionCounts(1, 0, 0), DecisionCounts(1, 2, 0), DecisionCounts(1, 2, 1)),
            seen,
        )
    }

    @Test fun resetKeepsTrashed() = runTest {
        store.set(listOf(1), KEPT, SWIPE); store.set(listOf(2), PENDING_TRASH, SWIPE); store.set(listOf(3), TRASHED, SWIPE)
        store.resetKeptAndPending()
        assertEquals(setOf(3L), store.decidedIds())
    }

    @Test fun pruneTrashedOnlyTouchesTrashedRows() = runTest {
        store.set(listOf(1, 2), TRASHED, SWIPE); store.set(listOf(3), KEPT, SWIPE)
        store.pruneTrashed(setOf(2))
        assertEquals(setOf(2L, 3L), store.decidedIds())
    }

    @Test fun clearRemovesRows() = runTest {
        store.set(listOf(1, 2), KEPT, SWIPE); store.clear(listOf(1))
        assertEquals(setOf(2L), store.decidedIds())
    }
}
