package io.github.lronsmith.photosorter.media

import android.database.Cursor
import android.provider.MediaStore.MediaColumns
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.MediaKind

/** Maps MediaStore rows to [MediaItem], tolerating missing metadata. */
object CursorMapping {
    /** Album name shown for files with no bucket (e.g. at the storage root). */
    private const val NO_BUCKET_NAME = "Internal storage"

    val IMAGE_PROJECTION: Array<String> = arrayOf(
        MediaColumns._ID,
        MediaColumns.DISPLAY_NAME,
        MediaColumns.SIZE,
        MediaColumns.MIME_TYPE,
        MediaColumns.DATE_TAKEN,
        MediaColumns.DATE_MODIFIED,
        MediaColumns.BUCKET_ID,
        MediaColumns.BUCKET_DISPLAY_NAME,
        MediaColumns.RELATIVE_PATH,
    )

    val VIDEO_PROJECTION: Array<String> = IMAGE_PROJECTION + MediaColumns.DURATION

    /** Maps the cursor's current row; the cursor must contain [kind]'s projection columns. */
    fun toMediaItem(c: Cursor, kind: MediaKind): MediaItem {
        val dateTaken = c.longOrNull(MediaColumns.DATE_TAKEN)?.takeIf { it > 0 }
            ?: c.longOrNull(MediaColumns.DATE_MODIFIED)?.let { it * 1000 }
            ?: 0L
        return MediaItem(
            id = c.getLong(c.getColumnIndexOrThrow(MediaColumns._ID)),
            kind = kind,
            albumId = c.stringOrNull(MediaColumns.BUCKET_ID) ?: "",
            albumName = c.stringOrNull(MediaColumns.BUCKET_DISPLAY_NAME) ?: NO_BUCKET_NAME,
            dateTaken = dateTaken,
            sizeBytes = c.longOrNull(MediaColumns.SIZE) ?: 0L,
            relativePath = c.stringOrNull(MediaColumns.RELATIVE_PATH) ?: "",
            displayName = c.stringOrNull(MediaColumns.DISPLAY_NAME) ?: "",
            mimeType = c.stringOrNull(MediaColumns.MIME_TYPE) ?: "",
            durationMs = if (kind == MediaKind.VIDEO) c.longOrNull(MediaColumns.DURATION) else null,
        )
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }
}
