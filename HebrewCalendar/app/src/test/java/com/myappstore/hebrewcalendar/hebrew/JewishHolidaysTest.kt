package com.myappstore.hebrewcalendar.hebrew

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JewishHolidaysTest {

    private fun namesOn(year: Int, month: Int, day: Int): List<String> {
        val date = LocalDate.of(year, month, day)
        return JewishHolidays.between(date, date)[date].orEmpty().map { it.name }
    }

    @Test
    fun fixedHolidays() {
        assertEquals(listOf("ראש השנה"), namesOn(2026, 9, 12))
        assertEquals(listOf("פסח"), namesOn(2026, 4, 2))
        assertEquals(listOf("חנוכה – נר א׳"), namesOn(2026, 12, 5))
        assertEquals(listOf("פורים"), namesOn(2027, 3, 23))
        assertEquals(listOf("פורים קטן"), namesOn(2027, 2, 21))
    }

    @Test
    fun yomHaatzmautMovesAwayFromShabbat() {
        assertEquals(listOf("יום העצמאות"), namesOn(2026, 4, 22)) // Wednesday, unchanged
        assertEquals(listOf("יום העצמאות"), namesOn(2024, 5, 14)) // 5 Iyar on Monday
        assertEquals(listOf("יום הזיכרון"), namesOn(2024, 5, 13))
        assertEquals(listOf("יום העצמאות"), namesOn(2025, 5, 1)) // 5 Iyar on Friday
        assertEquals(listOf("יום הזיכרון"), namesOn(2025, 4, 30))
    }

    @Test
    fun fastsPostponedFromShabbat() {
        assertEquals(listOf("תשעה באב"), namesOn(2025, 8, 3))
        assertTrue(namesOn(2025, 8, 2).isEmpty())
    }

    @Test
    fun roshChodeshCoversThirtiethOfPreviousMonth() {
        assertEquals(listOf("ראש חודש חשוון"), namesOn(2026, 10, 11))
        assertEquals(listOf("ראש חודש חשוון"), namesOn(2026, 10, 12))
    }

    @Test
    fun yomTovIsFlagged() {
        val date = LocalDate.of(2026, 9, 21) // Yom Kippur 5787
        assertTrue(JewishHolidays.between(date, date).getValue(date).single().isYomTov)
    }
}
