package com.radley.homefit.ui.onboarding

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class BirthYearTest {

    @Test
    fun `accepts plausible years and nothing else`() {
        assertEquals(1990, parseBirthYear("1990", thisYear = 2026))
        assertEquals(1990, parseBirthYear(" 1990 ", thisYear = 2026))
        assertEquals(2021, parseBirthYear("2021", thisYear = 2026))
        assertNull(parseBirthYear("2022", thisYear = 2026))
        assertNull(parseBirthYear("199", thisYear = 2026))
        assertNull(parseBirthYear("1899", thisYear = 2026))
        assertNull(parseBirthYear("", thisYear = 2026))
        assertNull(parseBirthYear("19x0", thisYear = 2026))
    }
}
