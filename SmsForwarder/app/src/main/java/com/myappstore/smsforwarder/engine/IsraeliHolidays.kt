package com.myappstore.smsforwarder.engine

import android.icu.util.Calendar
import android.icu.util.HebrewCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Yom Tov days as observed in Israel: Rosh Hashana (two days), Yom Kippur, the first
 * day of Sukkot, Shemini Atzeret, the first and seventh days of Pesach and Shavuot.
 * Dates come from the platform's ICU Hebrew calendar.
 */
object IsraeliHolidays {

    private val cache = HashMap<Int, Set<LocalDate>>()

    fun isYomTov(date: LocalDate): Boolean = date in forHebrewYear(hebrewYearOf(date))

    private fun hebrewYearOf(date: LocalDate): Int {
        val calendar = HebrewCalendar()
        calendar.timeInMillis = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return calendar.get(Calendar.EXTENDED_YEAR)
    }

    @Synchronized
    private fun forHebrewYear(year: Int): Set<LocalDate> = cache.getOrPut(year) {
        setOf(
            civilDate(year, HebrewCalendar.TISHRI, 1),
            civilDate(year, HebrewCalendar.TISHRI, 2),
            civilDate(year, HebrewCalendar.TISHRI, 10),
            civilDate(year, HebrewCalendar.TISHRI, 15),
            civilDate(year, HebrewCalendar.TISHRI, 22),
            civilDate(year, HebrewCalendar.NISAN, 15),
            civilDate(year, HebrewCalendar.NISAN, 21),
            civilDate(year, HebrewCalendar.SIVAN, 6),
        )
    }

    private fun civilDate(year: Int, month: Int, day: Int): LocalDate {
        val calendar = HebrewCalendar(year, month, day, 12, 0, 0)
        return Instant.ofEpochMilli(calendar.timeInMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
