package com.myappstore.hebrewcalendar.hebrew

import org.junit.Assert.assertEquals
import org.junit.Test

class HebrewNumeralsTest {

    @Test
    fun formatsDays() {
        assertEquals("א׳", HebrewNumerals.format(1))
        assertEquals("י׳", HebrewNumerals.format(10))
        assertEquals("ט״ו", HebrewNumerals.format(15))
        assertEquals("ט״ז", HebrewNumerals.format(16))
        assertEquals("כ״ו", HebrewNumerals.format(26))
        assertEquals("ל׳", HebrewNumerals.format(30))
    }

    @Test
    fun formatsYears() {
        assertEquals("תשפ״ז", HebrewNumerals.formatYear(5787))
        assertEquals("תש״ח", HebrewNumerals.formatYear(5708))
        assertEquals("ת״ת", HebrewNumerals.formatYear(5800))
        assertEquals("תתקע״ה", HebrewNumerals.formatYear(5975))
    }
}
