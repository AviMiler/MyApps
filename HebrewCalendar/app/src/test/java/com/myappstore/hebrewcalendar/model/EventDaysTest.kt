package com.myappstore.hebrewcalendar.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class EventDaysTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    private fun event(begin: Long, end: Long, allDay: Boolean) =
        CalendarEvent(1, "x", begin, end, allDay, 0, "cal")

    @Test
    fun allDayEventsAreReadInUtc() {
        val day = LocalDate.of(2026, 10, 26)
        val begin = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(day to day, EventDays.daysOf(event(begin, end, true), jerusalem))
    }

    @Test
    fun multiDayTimedEventCoversEachDayInRange() {
        val begin = LocalDate.of(2026, 10, 25).atTime(22, 0).atZone(jerusalem).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 27).atStartOfDay(jerusalem).toInstant().toEpochMilli()
        val days = EventDays.groupByDay(
            listOf(event(begin, end, false)), jerusalem, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 31)
        )
        assertEquals(setOf(LocalDate.of(2026, 10, 26)), days.keys)
    }
}
