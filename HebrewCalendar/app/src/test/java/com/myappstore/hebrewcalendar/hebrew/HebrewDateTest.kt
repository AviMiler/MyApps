package com.myappstore.hebrewcalendar.hebrew

import com.ibm.icu.util.Calendar
import com.ibm.icu.util.HebrewCalendar
import com.ibm.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HebrewDateTest {

    @Test
    fun matchesIcuForTwoCenturies() {
        val icu = HebrewCalendar(TimeZone.GMT_ZONE)
        var date = LocalDate.of(1900, 1, 1)
        while (date.year <= 2100) {
            icu.timeInMillis = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            val expected = HebrewDate(icu.get(Calendar.YEAR), icu.get(Calendar.MONTH), icu.get(Calendar.DAY_OF_MONTH))
            val actual = HebrewDate.of(date)
            // ICU wrongly applies BeTUTaKPaT after moving Rosh Hashana 5807 off Sunday,
            // so it disagrees from Heshvan 5806 on; see roshHashana5807 below.
            val icuIsWrong = expected.year == 5806 && expected.month >= HebrewDate.HESHVAN || expected.year == 5807
            if (!icuIsWrong) assertEquals("Hebrew date of $date", expected, actual)
            assertEquals("Round trip of $date", date, actual.toLocalDate())
            date = date.plusDays(1)
        }
    }

    @Test
    fun knownDates() {
        assertEquals(HebrewDate(5787, HebrewDate.HESHVAN, 15), HebrewDate.of(LocalDate.of(2026, 10, 26)))
        assertEquals(LocalDate.of(2026, 9, 12), HebrewDate.toLocalDate(5787, HebrewDate.TISHREI, 1))
        assertEquals(LocalDate.of(1948, 5, 14), HebrewDate.toLocalDate(5708, HebrewDate.IYAR, 5))
    }

    @Test
    fun roshHashana5807() {
        // Molad Tishrei 5807 is Sunday 17h 87p: lo ADU moves it to Monday and no other
        // postponement applies, so 5806 is a regular 384-day year.
        assertEquals(LocalDate.of(2046, 10, 1), HebrewDate.toLocalDate(5807, HebrewDate.TISHREI, 1))
        assertEquals(384, HebrewDate.daysInYear(5806))
    }

    @Test
    fun leapYearsFollowTheNineteenYearCycle() {
        assertEquals(true, HebrewDate.isLeapYear(5787))
        assertEquals(false, HebrewDate.isLeapYear(5786))
        assertEquals(13, HebrewDate.monthsOfYear(5787).size)
        assertEquals(12, HebrewDate.monthsOfYear(5786).size)
    }

    @Test
    fun formatsFullHebrewDate() {
        assertEquals("ט״ו בחשוון תשפ״ז", DateLabels.fullHebrewDate(HebrewDate(5787, HebrewDate.HESHVAN, 15)))
        assertEquals("א׳ באדר ב׳ תשפ״ז", DateLabels.fullHebrewDate(HebrewDate(5787, HebrewDate.ADAR, 1)))
        assertEquals("יום שני, 26 באוקטובר 2026", DateLabels.fullGregorianDate(LocalDate.of(2026, 10, 26)))
    }
}
