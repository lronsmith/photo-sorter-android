package io.github.lronsmith.photosorter.decisions

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lronsmith.photosorter.decisions.DecisionSource.DUPLICATE
import io.github.lronsmith.photosorter.decisions.DecisionSource.SWIPE
import io.github.lronsmith.photosorter.decisions.DecisionState.KEPT
import io.github.lronsmith.photosorter.decisions.DecisionState.PENDING_TRASH
import io.github.lronsmith.photosorter.decisions.DecisionState.TRASHED
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDecisionStoreTest {
    private lateinit var db: PhotoSorterDb
    private lateinit var store: RoomDecisionStore

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PhotoSorterDb::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomDecisionStore(db, clock = { 123L })
    }

    @After fun tearDown() = db.close()

    @Test fun setThenDecidedIds() = runTest {
        store.set(listOf(1, 2), KEPT, SWIPE); store.set(listOf(3), PENDING_TRASH, SWIPE)
        assertEquals(setOf(1L, 2L, 3L), store.decidedIds())
    }

    @Test fun setUpsertsState() = runTest {
        store.set(listOf(1), PENDING_TRASH, SWIPE); store.set(listOf(1), TRASHED, SWIPE)
        assertEquals(listOf(1L), store.idsIn(TRASHED)); assertTrue(store.idsIn(PENDING_TRASH).isEmpty())
    }

    @Test fun idsInIsAscending() = runTest {
        store.set(listOf(3, 1, 2), KEPT, SWIPE)
        assertEquals(listOf(1L, 2L, 3L), store.idsIn(KEPT))
    }

    @Test fun countsFlow() = runTest {
        store.set(listOf(1), KEPT, SWIPE); store.set(listOf(2, 3), PENDING_TRASH, SWIPE); store.set(listOf(4), TRASHED, DUPLICATE)
        assertEquals(DecisionCounts(1, 2, 1), store.observeCounts().first())
    }

    @Test fun countsAreZeroWhenEmpty() = runTest {
        assertEquals(DecisionCounts(0, 0, 0), store.observeCounts().first())
    }

    @Test fun countsFlowEmitsOnChange() = runTest {
        assertEquals(DecisionCounts(0, 0, 0), store.observeCounts().first())
        val seen = async { store.observeCounts().first { it.kept == 1 } }
        store.set(listOf(1), KEPT, SWIPE)
        assertEquals(DecisionCounts(1, 0, 0), seen.await())
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

    @Test fun storesSourceAndTimestamp() = runTest {
        store.set(listOf(9), TRASHED, DUPLICATE)
        val row = db.decisions().get(9)!!
        assertEquals(DUPLICATE, row.source); assertEquals(123L, row.decidedAt)
    }

    @Test fun largeBatchesBeyondSqliteVariableLimit() = runTest {
        val ids = (1L..2_000L).toList()
        store.set(ids, TRASHED, SWIPE)
        assertEquals(DecisionCounts(0, 0, 2_000), store.observeCounts().first())
        store.pruneTrashed(ids.drop(500).toSet())
        assertEquals(1_500, store.idsIn(TRASHED).size)
        store.clear(ids)
        assertTrue(store.decidedIds().isEmpty())
    }
}
