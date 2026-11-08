package io.github.lronsmith.photosorter.model

import io.github.lronsmith.photosorter.fakes.testItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemTest {
    @Test fun testItemDefaults() {
        val item = testItem(7)
        assertEquals(7L, item.id); assertEquals(7000L, item.dateTaken)
        assertEquals("DCIM/Camera/", item.relativePath); assertEquals(MediaKind.IMAGE, item.kind)
        assertNull(item.durationMs)
    }

    @Test fun videoTestItemHasDuration() {
        val v = testItem(1, kind = MediaKind.VIDEO)
        assertEquals("video/mp4", v.mimeType); assertEquals(1000L, v.durationMs)
    }
}
