package io.github.lronsmith.photosorter.media

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.MediaKind
import io.github.lronsmith.photosorter.model.contentUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * [MediaSource] backed by MediaStore's Images and Video collections. Kept thin: row mapping
 * lives in [CursorMapping]. Not unit-tested (Robolectric has no MediaStore provider).
 */
class MediaStoreSource(private val resolver: ContentResolver) : MediaSource {

    override suspend fun allItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        MediaKind.entries.flatMap { kind ->
            query(kind, queryArgs = null) { c -> CursorMapping.toMediaItem(c, kind) }
        }
    }

    override suspend fun trashedItems(ids: Collection<Long>): List<TrashedItem> = withContext(Dispatchers.IO) {
        ids.distinct().chunked(CHUNK_SIZE).flatMap { chunk ->
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaColumns._ID} IN (${chunk.joinToString(",") { "?" }})",
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    chunk.map { it.toString() }.toTypedArray(),
                )
            }
            MediaKind.entries.flatMap { kind ->
                query(kind, args, extraColumn = MediaColumns.DATE_EXPIRES) { c ->
                    val expiresIndex = c.getColumnIndexOrThrow(MediaColumns.DATE_EXPIRES)
                    val expiresAtMs = if (c.isNull(expiresIndex)) 0L else c.getLong(expiresIndex) * 1000
                    TrashedItem(CursorMapping.toMediaItem(c, kind), expiresAtMs)
                }
            }
        }
    }

    override suspend fun exists(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        // The default match excludes trashed and pending rows, so a trashed item reads as gone.
        resolver.query(item.contentUri(), arrayOf(MediaColumns._ID), null, null)
            ?.use { it.moveToFirst() } ?: false
    }

    override suspend fun openStream(item: MediaItem): InputStream? = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(item.contentUri())
        } catch (_: FileNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun <T> query(
        kind: MediaKind,
        queryArgs: Bundle?,
        extraColumn: String? = null,
        map: (Cursor) -> T,
    ): List<T> {
        val base = if (kind == MediaKind.VIDEO) CursorMapping.VIDEO_PROJECTION else CursorMapping.IMAGE_PROJECTION
        val projection = if (extraColumn == null) base else base + extraColumn
        val result = ArrayList<T>()
        resolver.query(collection(kind), projection, queryArgs, null)?.use { c ->
            while (c.moveToNext()) result += map(c)
        }
        return result
    }

    private fun collection(kind: MediaKind): Uri = when (kind) {
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    private companion object {
        /** IDs per trashed-item query, well under SQLite's bound-parameter limit. */
        const val CHUNK_SIZE = 500
    }
}
