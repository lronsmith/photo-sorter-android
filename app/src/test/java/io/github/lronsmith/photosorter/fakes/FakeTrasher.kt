package io.github.lronsmith.photosorter.fakes

import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.trash.RequestLauncher
import io.github.lronsmith.photosorter.trash.Trasher
import kotlinx.coroutines.CompletableDeferred

/**
 * Records trash/restore calls (as item ids) and returns [trashResult]/[restoreResult].
 * When [gate] is set, [trash] waits for it before returning. The launcher is ignored.
 */
class FakeTrasher(var instant: Boolean = false) : Trasher {
    val trashCalls: MutableList<List<Long>> = mutableListOf()
    val restoreCalls: MutableList<List<Long>> = mutableListOf()
    var trashResult: (List<MediaItem>) -> List<MediaItem> = { it }
    var restoreResult: (List<MediaItem>) -> List<MediaItem> = { it }
    var gate: CompletableDeferred<Unit>? = null

    override fun canTrashInstantly(): Boolean = instant

    override suspend fun trash(items: List<MediaItem>, launcher: RequestLauncher): List<MediaItem> {
        trashCalls += items.map { it.id }
        gate?.await()
        return trashResult(items)
    }

    override suspend fun restore(items: List<MediaItem>, launcher: RequestLauncher): List<MediaItem> {
        restoreCalls += items.map { it.id }
        return restoreResult(items)
    }
}
