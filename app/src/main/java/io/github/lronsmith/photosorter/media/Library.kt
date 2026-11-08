package io.github.lronsmith.photosorter.media

import io.github.lronsmith.photosorter.model.Album
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.SortOrder

/** Pure album grouping and queue ordering over a loaded library. */
object Library {
    private val oldestFirst: Comparator<MediaItem> = compareBy<MediaItem>({ it.dateTaken }, { it.id })

    /** Albums by bucket ID; the cover is the newest item; sorted by item count desc, then name. */
    fun albumsOf(items: List<MediaItem>): List<Album> =
        items.groupBy { it.albumId }
            .map { (id, members) ->
                val cover = members.maxWith(oldestFirst)
                Album(id = id, name = cover.albumName, count = members.size, cover = cover)
            }
            .sortedWith(compareByDescending<Album> { it.count }.thenBy { it.name })

    /** Drops items in [excludedAlbumIds] and sorts the rest by date taken (ties by id). */
    fun filterAndSort(items: List<MediaItem>, excludedAlbumIds: Set<String>, order: SortOrder): List<MediaItem> {
        val comparator = when (order) {
            SortOrder.OLDEST_FIRST -> oldestFirst
            SortOrder.NEWEST_FIRST -> oldestFirst.reversed()
        }
        return items.filter { it.albumId !in excludedAlbumIds }.sortedWith(comparator)
    }
}
