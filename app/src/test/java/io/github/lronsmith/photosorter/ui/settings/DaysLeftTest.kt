package io.github.lronsmith.photosorter.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DaysLeftTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test fun floorsToWholeDays() {
        val now = 0L
        assertEquals(6, daysLeft(6 * day + 5 * hour, now))
        assertEquals(0, daysLeft(3 * hour, now))
        assertEquals(0, daysLeft(-day, now))
    }

    @Test fun measuresFromNow() {
        assertEquals(1, daysLeft(10 * day, 9 * day - hour))
    }

    @Test fun labelsZeroAsToday() {
        assertEquals("today", daysLeftLabel(0))
        assertEquals("6d", daysLeftLabel(6))
    }
}
