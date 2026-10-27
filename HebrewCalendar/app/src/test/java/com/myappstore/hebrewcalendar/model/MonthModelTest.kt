package com.myappstore.hebrewcalendar.model

import com.myappstore.hebrewcalendar.hebrew.MonthPage
import com.myappstore.hebrewcalendar.hebrew.ViewMode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthModelTest {

    @Test
    fun marksTodayRestDaysHolidaysAndEvents() {
        val today = LocalDate.of(2026, 10, 26)
        val page = MonthPage.containing(LocalDate.of(2026, 9, 21), ViewMode.GREGORIAN)
        val event = CalendarEvent(1, "x", 0, 0, false, 0, "cal")
        val events = mapOf(LocalDate.of(2026, 9, 1) to listOf(event, event))
        val cells = MonthModel.build(page, today, events).flatten().filterNotNull()
        val yomKippur = cells.single { it.date == LocalDate.of(2026, 9, 21) }
        assertEquals("21", yomKippur.primaryLabel)
        assertEquals("י׳", yomKippur.secondaryLabel)
        assertEquals("יום כיפור", yomKippur.holiday?.name)
        assertTrue(yomKippur.isRestDay)
        assertTrue(cells.single { it.date == LocalDate.of(2026, 9, 5) }.isRestDay) // Shabbat
        assertEquals(2, cells.single { it.date == LocalDate.of(2026, 9, 1) }.events.size)
        assertTrue(cells.none { it.isToday })
    }
}
