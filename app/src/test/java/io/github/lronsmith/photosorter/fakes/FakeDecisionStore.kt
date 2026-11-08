package io.github.lronsmith.photosorter.fakes

import io.github.lronsmith.photosorter.decisions.Decision
import io.github.lronsmith.photosorter.decisions.DecisionCounts
import io.github.lronsmith.photosorter.decisions.DecisionSource
import io.github.lronsmith.photosorter.decisions.DecisionState
import io.github.lronsmith.photosorter.decisions.DecisionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [DecisionStore] with the same behaviour as RoomDecisionStore; [rows] is the current state. */
class FakeDecisionStore(private val clock: () -> Long = { 0L }) : DecisionStore {
    private val state = MutableStateFlow<Map<Long, Decision>>(emptyMap())

    val rows: Map<Long, Decision> get() = state.value

    override fun observeCounts(): Flow<DecisionCounts> = state.map { rows ->
        val byState = rows.values.groupingBy { it.state }.eachCount()
        DecisionCounts(
            kept = byState[DecisionState.KEPT] ?: 0,
            pending = byState[DecisionState.PENDING_TRASH] ?: 0,
            trashed = byState[DecisionState.TRASHED] ?: 0,
        )
    }.distinctUntilChanged()

    override suspend fun decidedIds(): Set<Long> = rows.keys.toSet()

    override suspend fun idsIn(state: DecisionState): List<Long> =
        rows.values.filter { it.state == state }.map { it.mediaId }.sorted()

    override suspend fun set(mediaIds: Collection<Long>, state: DecisionState, source: DecisionSource) {
        val now = clock()
        this.state.update { rows -> rows + mediaIds.associateWith { Decision(it, state, source, now) } }
    }

    override suspend fun clear(mediaIds: Collection<Long>) {
        state.update { it - mediaIds.toSet() }
    }

    override suspend fun resetKeptAndPending() {
        state.update { rows -> rows.filterValues { it.state == DecisionState.TRASHED } }
    }

    override suspend fun pruneTrashed(stillTrashed: Set<Long>) {
        state.update { rows -> rows.filterValues { it.state != DecisionState.TRASHED || it.mediaId in stillTrashed } }
    }
}
