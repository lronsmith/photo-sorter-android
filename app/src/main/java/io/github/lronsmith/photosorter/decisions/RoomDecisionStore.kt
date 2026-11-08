package io.github.lronsmith.photosorter.decisions

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Ids per statement; well under SQLite's bound-variable limit (999 on older Android). */
private const val CHUNK = 500

class RoomDecisionStore(
    private val db: PhotoSorterDb,
    private val clock: () -> Long = System::currentTimeMillis,
) : DecisionStore {
    private val dao = db.decisions()

    override fun observeCounts(): Flow<DecisionCounts> = dao.observeCounts().distinctUntilChanged()

    override suspend fun decidedIds(): Set<Long> = dao.allIds().toSet()

    override suspend fun idsIn(state: DecisionState): List<Long> = dao.idsIn(state)

    override suspend fun set(mediaIds: Collection<Long>, state: DecisionState, source: DecisionSource) {
        if (mediaIds.isEmpty()) return
        val now = clock()
        db.withTransaction {
            mediaIds.chunked(CHUNK).forEach { chunk ->
                dao.upsert(chunk.map { Decision(it, state, source, now) })
            }
        }
    }

    override suspend fun clear(mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        db.withTransaction { deleteChunked(mediaIds) }
    }

    override suspend fun resetKeptAndPending() = dao.deleteKeptAndPending()

    override suspend fun pruneTrashed(checked: Collection<Long>, stillTrashed: Set<Long>) {
        val gone = checked.filterNot { it in stillTrashed }
        if (gone.isEmpty()) return
        db.withTransaction { gone.chunked(CHUNK).forEach { dao.deleteTrashed(it) } }
    }

    private suspend fun deleteChunked(mediaIds: Collection<Long>) {
        mediaIds.chunked(CHUNK).forEach { dao.delete(it) }
    }
}
