package io.github.lronsmith.photosorter.media

import io.github.lronsmith.photosorter.model.MediaItem
import java.io.InputStream

/** An item in MediaStore's trash; [expiresAt] (ms since epoch) is when Android purges it. */
data class TrashedItem(val item: MediaItem, val expiresAt: Long)

/** Read access to the phone's photo and video library. */
interface MediaSource {
    /** Every live (not trashed, not pending) image and video. */
    suspend fun allItems(): List<MediaItem>

    /** The items among [ids] that are currently in the trash; the others are omitted. */
    suspend fun trashedItems(ids: Collection<Long>): List<TrashedItem>

    /** Whether [item] is still live in the library (false once trashed or deleted). */
    suspend fun exists(item: MediaItem): Boolean

    /** Opens [item]'s bytes, or null if it is gone or unreadable. The caller closes the stream. */
    suspend fun openStream(item: MediaItem): InputStream?
}
