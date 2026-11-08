package io.github.lronsmith.photosorter.decisions

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Methods taking id lists bind one SQL variable per id; callers keep lists to at most 500. */
@Dao
interface DecisionDao {
    @Upsert
    suspend fun upsert(decisions: List<Decision>)

    @Query("SELECT * FROM decisions WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Long): Decision?

    @Query("SELECT mediaId FROM decisions")
    suspend fun allIds(): List<Long>

    @Query("SELECT mediaId FROM decisions WHERE state = :state ORDER BY mediaId")
    suspend fun idsIn(state: DecisionState): List<Long>

    @Query(
        "SELECT COALESCE(SUM(state = 'KEPT'), 0) AS kept, " +
            "COALESCE(SUM(state = 'PENDING_TRASH'), 0) AS pending, " +
            "COALESCE(SUM(state = 'TRASHED'), 0) AS trashed FROM decisions",
    )
    fun observeCounts(): Flow<DecisionCounts>

    @Query("DELETE FROM decisions WHERE mediaId IN (:mediaIds)")
    suspend fun delete(mediaIds: List<Long>)

    @Query("DELETE FROM decisions WHERE state = 'TRASHED' AND mediaId IN (:mediaIds)")
    suspend fun deleteTrashed(mediaIds: List<Long>)

    @Query("DELETE FROM decisions WHERE state IN ('KEPT', 'PENDING_TRASH')")
    suspend fun deleteKeptAndPending()
}
