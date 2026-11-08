package com.myappstore.hebrewcalendar.hebrew

import java.time.DayOfWeek
import java.time.LocalDate

/** Hebrew-language labels for Hebrew and Gregorian dates. */
object DateLabels {

    private val HEBREW_MONTHS = arrayOf(
        "תשרי", "חשוון", "כסלו", "טבת", "שבט", "אדר א׳", "אדר",
        "ניסן", "אייר", "סיוון", "תמוז", "אב", "אלול"
    )

    private val GREGORIAN_MONTHS = arrayOf(
        "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
        "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר"
    )

    /** Sunday-first, matching the grid columns. */
    val WEEKDAY_LETTERS = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")

    private val WEEKDAY_NAMES = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")

    /** @return e.g. "חשוון", or "אדר ב׳" for Adar in a leap year. */
    fun hebrewMonthName(year: Int, month: Int): String =
        if (month == HebrewDate.ADAR && HebrewDate.isLeapYear(year)) "אדר ב׳" else HEBREW_MONTHS[month]

    fun gregorianMonthName(month: Int): String = GREGORIAN_MONTHS[month - 1]

    /** @return e.g. "כ״ה בחשוון". */
    fun hebrewDayAndMonth(date: HebrewDate): String =
        "${HebrewNumerals.format(date.day)} ב${hebrewMonthName(date.year, date.month)}"

    /** @return e.g. "כ״ה בחשוון תשפ״ז". */
    fun fullHebrewDate(date: HebrewDate): String =
        "${hebrewDayAndMonth(date)} ${HebrewNumerals.formatYear(date.year)}"

    /** @return e.g. "יום שני, 26 באוקטובר 2026". */
    fun fullGregorianDate(date: LocalDate): String {
        val weekday = if (date.dayOfWeek == DayOfWeek.SATURDAY) "שבת" else "יום ${weekdayName(date)}"
        return "$weekday, ${date.dayOfMonth} ב${gregorianMonthName(date.monthValue)} ${date.year}"
    }

    /** Sunday = 0 ... Saturday = 6, the column index in a Hebrew-style week. */
    fun columnOf(date: LocalDate): Int = date.dayOfWeek.value % 7

    private fun weekdayName(date: LocalDate): String = WEEKDAY_NAMES[columnOf(date)]
}
