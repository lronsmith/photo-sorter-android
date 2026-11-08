package io.github.lronsmith.photosorter.fakes

import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.media.TrashedItem
import io.github.lronsmith.photosorter.model.MediaItem
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * In-memory [MediaSource]: [allItems] returns [items]; [trashedItems] returns the [trashed]
 * entries whose key is requested; [exists] is false iff the id is in [missing]; [openStream]
 * serves [contents] (null when absent).
 */
class FakeMediaSource(var items: List<MediaItem> = emptyList()) : MediaSource {
    val trashed: MutableMap<Long, TrashedItem> = mutableMapOf()
    val missing: MutableSet<Long> = mutableSetOf()
    val contents: MutableMap<Long, ByteArray> = mutableMapOf()

    override suspend fun allItems(): List<MediaItem> = items

    override suspend fun trashedItems(ids: Collection<Long>): List<TrashedItem> {
        val wanted = ids.toSet()
        return trashed.filterKeys { it in wanted }.values.toList()
    }

    override suspend fun exists(item: MediaItem): Boolean = item.id !in missing

    override suspend fun openStream(item: MediaItem): InputStream? =
        contents[item.id]?.let { ByteArrayInputStream(it) }
}
