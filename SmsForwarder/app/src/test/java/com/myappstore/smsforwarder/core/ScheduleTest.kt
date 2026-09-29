package com.myappstore.smsforwarder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    @Test
    fun dayMask() {
        assertTrue(Days.has(Days.SUN_TO_THU, DayOfWeek.SUNDAY))
        assertTrue(Days.has(Days.SUN_TO_THU, DayOfWeek.THURSDAY))
        assertFalse(Days.has(Days.SUN_TO_THU, DayOfWeek.FRIDAY))
        assertFalse(Days.has(Days.SUN_TO_THU, DayOfWeek.SATURDAY))
        assertEquals(Days.SUN_TO_FRI, Days.toggle(Days.SUN_TO_THU, 5))
    }

    @Test
    fun daytimeWindow() {
        val work = TimeWindow(Days.SUN_TO_THU, 8 * 60, 18 * 60)
        // 2026-09-29 is a Tuesday.
        assertTrue(work.contains(LocalDateTime.of(2026, 9, 29, 8, 0)))
        assertTrue(work.contains(LocalDateTime.of(2026, 9, 29, 17, 59)))
        assertFalse(work.contains(LocalDateTime.of(2026, 9, 29, 18, 0)))
        assertFalse(work.contains(LocalDateTime.of(2026, 10, 2, 10, 0))) // Friday
    }

    @Test
    fun overnightWindowBelongsToTheStartDay() {
        val night = TimeWindow(Days.SUN_TO_THU, 22 * 60, 7 * 60)
        val thursdayNight = LocalDateTime.of(2026, 10, 1, 23, 30)
        val fridayMorning = LocalDateTime.of(2026, 10, 2, 6, 30)
        val saturdayMorning = LocalDateTime.of(2026, 10, 3, 6, 30)
        assertTrue(night.contains(thursdayNight))
        assertTrue(night.contains(fridayMorning))
        assertFalse(night.contains(saturdayMorning))
        assertEquals(LocalDateTime.of(2026, 10, 2, 7, 0), night.occurrenceAt(fridayMorning)!!.second)
    }

    @Test
    fun sunsetMatchesReferenceTimes() {
        // Reference values computed independently (astral, sea level, zenith 90.833).
        val jerusalem = Cities.byId("jerusalem")
        val eilat = Cities.byId("eilat")
        assertClose(jerusalem, LocalDate.of(2026, 1, 2), "16:46:48")
        assertClose(jerusalem, LocalDate.of(2026, 6, 19), "19:47:03")
        assertClose(jerusalem, LocalDate.of(2026, 9, 25), "18:31:56")
        assertClose(jerusalem, LocalDate.of(2026, 12, 18), "16:37:49")
        assertClose(eilat, LocalDate.of(2026, 3, 20), "17:51:15")
        assertClose(eilat, LocalDate.of(2026, 10, 30), "16:55:15")
    }

    private fun assertClose(city: City, date: LocalDate, expected: String) {
        val sunset = SolarTimes.sunset(date, city.latitude, city.longitude, zone)
        assertNotNull(sunset)
        val want = date.atTime(java.time.LocalTime.parse(expected)).atZone(zone)
        val diff = Duration.between(want, sunset!!).abs()
        assertTrue("${city.id} $date: got ${sunset.toLocalTime()} want $expected", diff.seconds <= 120)
    }

    @Test
    fun shabbatWindowRunsFromCandleLightingToNightfall() {
        val calendar = RestCalendar(Cities.byId("jerusalem"), zone, candleMinutes = 40, havdalahMinutes = 40) { false }
        val saturdayNoon = LocalDateTime.of(2026, 10, 3, 12, 0).atZone(zone)
        val window = calendar.activeAt(saturdayNoon)
        assertNotNull(window)
        assertEquals(LocalDate.of(2026, 10, 3), window!!.firstDay)
        assertEquals(DayOfWeek.FRIDAY, window.start.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, window.end.dayOfWeek)
        assertFalse(window.includesHoliday)
        // Friday morning is before candle lighting.
        assertNull(calendar.activeAt(LocalDateTime.of(2026, 10, 2, 10, 0).atZone(zone)))
        // Friday late evening is inside.
        assertNotNull(calendar.activeAt(LocalDateTime.of(2026, 10, 2, 21, 0).atZone(zone)))
        // Saturday late night is after havdalah.
        assertNull(calendar.activeAt(LocalDateTime.of(2026, 10, 3, 22, 0).atZone(zone)))
    }

    @Test
    fun holidayNextToShabbatMergesIntoOneWindow() {
        // Rosh Hashana 5787 falls on Saturday-Sunday, 12-13 September 2026.
        val holidays = setOf(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13))
        val calendar = RestCalendar(Cities.byId("tel_aviv"), zone, 20, 40) { it in holidays }
        val window = calendar.activeAt(LocalDateTime.of(2026, 9, 13, 9, 0).atZone(zone))
        assertNotNull(window)
        assertEquals(LocalDate.of(2026, 9, 12), window!!.firstDay)
        assertEquals(LocalDate.of(2026, 9, 13), window.lastDay)
        assertEquals(DayOfWeek.FRIDAY, window.start.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, window.end.dayOfWeek)
        assertTrue(window.includesHoliday)
    }

    @Test
    fun nextWindowIsFoundAhead() {
        val calendar = RestCalendar(Cities.byId("haifa"), zone, 30, 40) { false }
        val tuesday = LocalDateTime.of(2026, 9, 29, 10, 0).atZone(zone)
        val next = calendar.currentOrNext(tuesday)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 10, 3), next!!.firstDay)
    }
}
