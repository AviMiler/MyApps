package com.myappstore.hebrewcalendar.hebrew

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthPageTest {

    @Test
    fun hebrewPageSpansTheHebrewMonth() {
        val page = MonthPage.containing(LocalDate.of(2026, 10, 26), ViewMode.HEBREW)
        assertEquals(LocalDate.of(2026, 10, 12), page.firstDay)
        assertEquals(LocalDate.of(2026, 11, 10), page.lastDay)
        assertEquals("חשוון תשפ״ז", page.title)
        assertEquals("אוקטובר – נובמבר 2026", page.subtitle)
        assertEquals(listOf(null, LocalDate.of(2026, 10, 12)), page.weeks().first().take(2))
        assertEquals("כסלו תשפ״ז", page.next().title)
        assertEquals("תשרי תשפ״ז", page.previous().title)
        assertEquals("אדר א׳ תשפ״ז", page.shiftedBy(4).title)
    }

    @Test
    fun gregorianPageSpansTheCivilMonth() {
        val page = MonthPage.containing(LocalDate.of(2026, 12, 15), ViewMode.GREGORIAN)
        assertEquals("דצמבר 2026", page.title)
        assertEquals("כסלו – טבת תשפ״ז", page.subtitle)
        assertEquals("ינואר 2027", page.next().title)
        page.weeks().forEach { assertEquals(7, it.size) }
    }
}
