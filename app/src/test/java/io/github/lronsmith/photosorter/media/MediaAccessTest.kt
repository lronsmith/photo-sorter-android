package io.github.lronsmith.photosorter.media

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import io.github.lronsmith.photosorter.media.AccessLevel.FULL
import io.github.lronsmith.photosorter.media.AccessLevel.NONE
import io.github.lronsmith.photosorter.media.AccessLevel.PARTIAL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAccessTest {
    @Test fun permissionsPerSdk() {
        assertArrayEquals(arrayOf(READ_EXTERNAL_STORAGE), MediaAccess.permissionsToRequest(30))
        assertArrayEquals(arrayOf(READ_EXTERNAL_STORAGE), MediaAccess.permissionsToRequest(32))
        assertArrayEquals(arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO), MediaAccess.permissionsToRequest(33))
        assertArrayEquals(
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED),
            MediaAccess.permissionsToRequest(34),
        )
    }

    @Test fun levels() {
        assertEquals(FULL, MediaAccess.level(34) { it != READ_MEDIA_VISUAL_USER_SELECTED })
        assertEquals(PARTIAL, MediaAccess.level(34) { it == READ_MEDIA_VISUAL_USER_SELECTED })
        assertEquals(NONE, MediaAccess.level(33) { it == READ_MEDIA_IMAGES })
        assertEquals(FULL, MediaAccess.level(31) { it == READ_EXTERNAL_STORAGE })
        assertEquals(NONE, MediaAccess.level(30) { false })
    }

    @Test fun userSelectedIgnoredBelow34() {
        assertEquals(NONE, MediaAccess.level(33) { it == READ_MEDIA_VISUAL_USER_SELECTED })
    }

    @Test fun readExternalIgnoredFrom33() {
        assertEquals(NONE, MediaAccess.level(33) { it == READ_EXTERNAL_STORAGE })
    }
}
