package io.github.lronsmith.photosorter.trash

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
