package io.github.lronsmith.photosorter.decisions

import kotlinx.coroutines.flow.Flow

/** The user's per-item decisions, keyed by MediaStore id. */
interface DecisionStore {
    /** Emits the current counts, then again whenever they change. */
    fun observeCounts(): Flow<DecisionCounts>

    /** Ids with a decision in any state. */
    suspend fun decidedIds(): Set<Long>

    /** Ids in [state], ascending. */
    suspend fun idsIn(state: DecisionState): List<Long>

    /** Upserts: each id ends up in [state] with [source], stamped with the current time. */
    suspend fun set(mediaIds: Collection<Long>, state: DecisionState, source: DecisionSource)

    /** Removes the decisions for [mediaIds]; ids without one are ignored. */
    suspend fun clear(mediaIds: Collection<Long>)

    /** Deletes every KEPT and PENDING_TRASH row; TRASHED rows stay (they back the Trashed list). */
    suspend fun resetKeptAndPending()

    /**
     * Deletes the TRASHED rows whose id is in [checked] (the TRASHED ids the caller looked up)
     * but not in [stillTrashed]. Rows in other states, and TRASHED rows written after the
     * caller's snapshot, are untouched.
     */
    suspend fun pruneTrashed(checked: Collection<Long>, stillTrashed: Set<Long>)
}
