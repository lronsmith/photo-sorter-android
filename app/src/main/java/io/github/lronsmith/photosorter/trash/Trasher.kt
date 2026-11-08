package io.github.lronsmith.photosorter.trash

import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.model.MediaItem

/** Maximum number of URIs carried by one MediaStore trash/restore request. */
const val TRASH_CHUNK = 500

/**
 * Moves media to the system trash and back. Never deletes permanently.
 * Each call returns only the items whose request chunk succeeded.
 */
interface Trasher {
    /** True when trash requests complete without a confirmation dialog ("Media management" granted). */
    fun canTrashInstantly(): Boolean

    suspend fun trash(items: List<MediaItem>, launcher: RequestLauncher): List<MediaItem>

    suspend fun restore(items: List<MediaItem>, launcher: RequestLauncher): List<MediaItem>
}

/**
 * Trashes [items] and returns the ones now in the system trash. Items whose request reported
 * failure are checked against [media]: a request can fail (e.g. its screen closed) after the
 * system already trashed the item.
 */
suspend fun Trasher.trashVerified(
    items: List<MediaItem>,
    launcher: RequestLauncher,
    media: MediaSource,
): List<MediaItem> {
    val reported = trash(items, launcher)
    if (reported.size == items.size) return reported
    val reportedIds = reported.mapTo(HashSet()) { it.id }
    val unconfirmed = items.filter { it.id !in reportedIds }
    val inTrash = media.trashedItems(unconfirmed.map { it.id }).mapTo(HashSet()) { it.item.id }
    return reported + unconfirmed.filter { it.id in inTrash }
}
