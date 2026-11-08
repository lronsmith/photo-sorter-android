package io.github.lronsmith.photosorter.media

import io.github.lronsmith.photosorter.fakes.testItem
import io.github.lronsmith.photosorter.model.SortOrder.NEWEST_FIRST
import io.github.lronsmith.photosorter.model.SortOrder.OLDEST_FIRST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val tied = listOf(testItem(1, dateTaken = 9), testItem(3, dateTaken = 5), testItem(2, dateTaken = 5))

    @Test fun oldestFirstWithIdTieBreak() {
        assertEquals(listOf(2L, 3L, 1L), Library.filterAndSort(tied, emptySet(), OLDEST_FIRST).map { it.id })
    }

    @Test fun newestFirstIsReverse() {
        val ids = Library.filterAndSort(tied, emptySet(), NEWEST_FIRST).map { it.id }
        assertEquals(listOf(1L, 3L, 2L), ids)
    }

    @Test fun excludedAlbumsFiltered() {
        val items = listOf(
            testItem(1, albumId = "camera"),
            testItem(2, albumId = "wa", albumName = "WhatsApp"),
            testItem(3, albumId = "camera"),
        )
        val result = Library.filterAndSort(items, setOf("wa"), OLDEST_FIRST)
        assertEquals(2, result.size)
        assertTrue(result.all { it.albumId == "camera" })
    }

    @Test fun albumsCountedWithNewestCover() {
        val items = listOf(
            testItem(1, dateTaken = 1, albumId = "camera"),
            testItem(3, dateTaken = 9, albumId = "wa", albumName = "WhatsApp"),
            testItem(2, dateTaken = 5, albumId = "camera"),
        )
        val albums = Library.albumsOf(items)
        assertEquals(listOf("camera" to 2, "wa" to 1), albums.map { it.id to it.count })
        assertEquals(2L, albums[0].cover.id)
        assertEquals("Camera", albums[0].name)
    }

    @Test fun albumsWithEqualCountSortedByName() {
        val items = listOf(
            testItem(1, albumId = "z", albumName = "Zebra"),
            testItem(2, albumId = "a", albumName = "Apple"),
        )
        assertEquals(listOf("Apple", "Zebra"), Library.albumsOf(items).map { it.name })
    }
}
