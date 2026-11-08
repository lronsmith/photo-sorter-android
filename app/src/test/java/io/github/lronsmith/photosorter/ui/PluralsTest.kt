package io.github.lronsmith.photosorter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralsTest {
    @Test fun oneIsSingular() = assertEquals("1 copy", plural(1, "copy", "copies"))

    @Test fun zeroAndManyArePlural() {
        assertEquals("0 copies", plural(0, "copy", "copies"))
        assertEquals("2 copies", plural(2, "copy", "copies"))
    }
}
