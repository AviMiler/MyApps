package com.myappstore.hebrewcalendar.hebrew

import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.ADAR
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.ADAR_I
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.AV
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.ELUL
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.IYAR
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.KISLEV
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.NISAN
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.SHEVAT
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.SIVAN
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.TAMMUZ
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.TEVET
import com.myappstore.hebrewcalendar.hebrew.HebrewDate.Companion.TISHREI
import java.time.DayOfWeek
import java.time.LocalDate

/** A holiday, fast or Rosh Chodesh; [isYomTov] marks days of rest (shown like Shabbat). */
data class Holiday(val name: String, val isYomTov: Boolean = false)

/** Jewish holidays as observed in Israel, including Shabbat-related postponements. */
object JewishHolidays {

    /** @return holidays for every date in [first]..[last] that has any. */
    fun between(first: LocalDate, last: LocalDate): Map<LocalDate, List<Holiday>> {
        val years = HebrewDate.of(first).year..HebrewDate.of(last).year
        return years.flatMap { forYear(it).entries }
            .filter { it.key in first..last }
            .associate { it.key to it.value }
    }

    /** @return holidays of Hebrew [year] (1 Tishrei to 29 Elul) keyed by Gregorian date. */
    fun forYear(year: Int): Map<LocalDate, List<Holiday>> {
        val result = sortedMapOf<LocalDate, MutableList<Holiday>>()
        fun date(month: Int, day: Int) = HebrewDate.toLocalDate(year, month, day)
        fun addOn(date: LocalDate, name: String, isYomTov: Boolean = false) {
            result.getOrPut(date) { mutableListOf() }.add(Holiday(name, isYomTov))
        }
        fun add(month: Int, day: Int, name: String, isYomTov: Boolean = false) =
            addOn(date(month, day), name, isYomTov)

        add(TISHREI, 1, "ראש השנה", isYomTov = true)
        add(TISHREI, 2, "ראש השנה ב׳", isYomTov = true)
        addOn(postponedFromShabbat(date(TISHREI, 3)), "צום גדליה")
        add(TISHREI, 9, "ערב יום כיפור")
        add(TISHREI, 10, "יום כיפור", isYomTov = true)
        add(TISHREI, 14, "ערב סוכות")
        add(TISHREI, 15, "סוכות", isYomTov = true)
        for (day in 16..20) add(TISHREI, day, "חול המועד סוכות")
        add(TISHREI, 21, "הושענא רבה")
        add(TISHREI, 22, "שמחת תורה", isYomTov = true)

        add(KISLEV, 24, "ערב חנוכה")
        val hanukkah = date(KISLEV, 25)
        for (day in 0L..7L) {
            addOn(hanukkah.plusDays(day), "חנוכה – נר ${HebrewNumerals.format(day.toInt() + 1)}")
        }
        add(TEVET, 10, "עשרה בטבת")
        add(SHEVAT, 15, "ט״ו בשבט")

        if (HebrewDate.isLeapYear(year)) add(ADAR_I, 14, "פורים קטן")
        // Ta'anit Esther moves back to Thursday when 13 Adar is Shabbat.
        val esther = date(ADAR, 13)
        addOn(if (esther.dayOfWeek == DayOfWeek.SATURDAY) esther.minusDays(2) else esther, "תענית אסתר")
        add(ADAR, 14, "פורים")
        add(ADAR, 15, "שושן פורים")

        add(NISAN, 14, "ערב פסח")
        add(NISAN, 15, "פסח", isYomTov = true)
        for (day in 16..20) add(NISAN, day, "חול המועד פסח")
        add(NISAN, 21, "שביעי של פסח", isYomTov = true)
        addOn(yomHashoah(date(NISAN, 27)), "יום השואה")
        val independence = yomHaatzmaut(date(IYAR, 5))
        addOn(independence.minusDays(1), "יום הזיכרון")
        addOn(independence, "יום העצמאות")
        add(IYAR, 18, "ל״ג בעומר")
        add(IYAR, 28, "יום ירושלים")
        add(SIVAN, 5, "ערב שבועות")
        add(SIVAN, 6, "שבועות", isYomTov = true)

        addOn(postponedFromShabbat(date(TAMMUZ, 17)), "שבעה עשר בתמוז")
        addOn(postponedFromShabbat(date(AV, 9)), "תשעה באב")
        add(AV, 15, "ט״ו באב")
        add(ELUL, 29, "ערב ראש השנה")

        addRoshChodesh(year, ::addOn)
        return result
    }

    private fun addRoshChodesh(year: Int, add: (LocalDate, String, Boolean) -> Unit) {
        val months = HebrewDate.monthsOfYear(year)
        months.zipWithNext { previous, month ->
            val name = "ראש חודש ${DateLabels.hebrewMonthName(year, month)}"
            val first = HebrewDate.toLocalDate(year, month, 1)
            if (HebrewDate.monthLength(year, previous) == 30) add(first.minusDays(1), name, false)
            add(first, name, false)
        }
    }

    /** Fasts that fall on Shabbat are observed on Sunday. */
    private fun postponedFromShabbat(date: LocalDate): LocalDate =
        if (date.dayOfWeek == DayOfWeek.SATURDAY) date.plusDays(1) else date

    /** 27 Nisan moves to Thursday when on Friday and to Monday when on Sunday. */
    private fun yomHashoah(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.FRIDAY -> date.minusDays(1)
        DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
    }

    /** 5 Iyar moves so neither it nor Yom HaZikaron (the day before) touches Shabbat. */
    private fun yomHaatzmaut(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.FRIDAY -> date.minusDays(1)
        DayOfWeek.SATURDAY -> date.minusDays(2)
        DayOfWeek.MONDAY -> date.plusDays(1)
        else -> date
    }
}
