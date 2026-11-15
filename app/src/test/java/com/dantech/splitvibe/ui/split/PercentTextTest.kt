package com.dantech.splitvibe.ui.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How a percentage is read from what someone types, and written back. */
class PercentTextTest {

    @Test
    fun `a percentage is read in hundredths with either decimal mark`() {
        assertEquals(2_500L, parsePercent("25"))
        assertEquals(3_333L, parsePercent("33,33"))
        assertEquals(1_250L, parsePercent("12.5"))
        assertEquals(1_200L, parsePercent("12."))
    }

    @Test
    fun `more than two decimals, a sign or anything else is not a percentage`() {
        assertNull("not 33,333%", parsePercent("33.333"))
        assertNull(parsePercent("-5"))
        assertNull(parsePercent("1e2"))
        assertNull(parsePercent(""))
    }

    @Test
    fun `a percentage is written without zeros that say nothing`() {
        assertEquals("25", percentText(2_500))
        assertEquals("12.5", percentText(1_250))
        assertEquals("33.33", percentText(3_333))
    }
}
