package com.myappstore.hebrewcalendar.hebrew

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HebrewAnniversaryTest {

    @Test
    fun followsTheHebrewDate() {
        val dates = HebrewAnniversary.dates(HebrewDate.of(LocalDate.of(2026, 10, 26)), 3)
        assertEquals(3, dates.size)
        dates.forEach { assertEquals(HebrewDate(HebrewDate.of(it).year, HebrewDate.HESHVAN, 15), HebrewDate.of(it)) }
    }

    @Test
    fun adarIMapsToAdarInCommonYears() {
        val dates = HebrewAnniversary.dates(HebrewDate(5787, HebrewDate.ADAR_I, 30), 2)
        assertEquals(HebrewDate(5788, HebrewDate.ADAR, 29), HebrewDate.of(dates[1]))
    }
}
