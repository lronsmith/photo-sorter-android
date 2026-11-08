package io.github.lronsmith.photosorter.media

import android.database.MatrixCursor
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.MediaKind
import io.github.lronsmith.photosorter.model.MediaKind.IMAGE
import io.github.lronsmith.photosorter.model.MediaKind.VIDEO
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CursorMappingTest {

    /** Builds a one-row cursor with [kind]'s projection, then maps that row. */
    private fun map(kind: MediaKind, vararg row: Any?): MediaItem {
        val projection = if (kind == VIDEO) CursorMapping.VIDEO_PROJECTION else CursorMapping.IMAGE_PROJECTION
        val cursor = MatrixCursor(projection)
        cursor.addRow(row)
        cursor.moveToFirst()
        return CursorMapping.toMediaItem(cursor, kind)
    }

    @Test fun mapsImageRow() {
        val item = map(IMAGE, 5L, "a.jpg", 2048L, "image/jpeg", 1_600_000_000_000L, 1_500_000_000L, "b1", "Camera", "DCIM/Camera/")
        assertEquals(MediaItem(5, IMAGE, "b1", "Camera", 1_600_000_000_000, 2048, "DCIM/Camera/", "a.jpg", "image/jpeg", null), item)
    }

    @Test fun mapsVideoDuration() {
        val item = map(VIDEO, 6L, "v.mp4", 4096L, "video/mp4", 1_600_000_000_000L, 1_500_000_000L, "b1", "Camera", "DCIM/Camera/", 4200L)
        assertEquals(4200L, item.durationMs)
        assertEquals(VIDEO, item.kind)
    }

    @Test fun mapsRowWithMissingMetadata() {
        val item = map(IMAGE, 7L, null, null, null, null, 1_500_000_000L, null, null, null)
        assertEquals(1_500_000_000_000, item.dateTaken)
        assertEquals("", item.albumId)
        assertEquals("Internal storage", item.albumName)
        assertEquals("", item.relativePath)
        assertEquals("", item.displayName)
        assertEquals("", item.mimeType)
        assertEquals(0L, item.sizeBytes)
    }

    @Test fun zeroDateTakenFallsBack() {
        val item = map(IMAGE, 8L, "b.jpg", 1L, "image/jpeg", 0L, 7L, "b1", "Camera", "DCIM/Camera/")
        assertEquals(7000L, item.dateTaken)
    }

    @Test fun noDatesAtAllGivesZero() {
        val item = map(IMAGE, 9L, "c.jpg", 1L, "image/jpeg", null, null, "b1", "Camera", "DCIM/Camera/")
        assertEquals(0L, item.dateTaken)
    }
}
