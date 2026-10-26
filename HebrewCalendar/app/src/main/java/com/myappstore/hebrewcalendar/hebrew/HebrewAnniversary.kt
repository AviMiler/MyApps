package com.myappstore.hebrewcalendar.hebrew

import java.time.LocalDate

/**
 * Yearly recurrence by Hebrew date (birthdays, memorial days), which Google
 * Calendar cannot express natively, so the app writes one event per year.
 */
object HebrewAnniversary {

    /**
     * @return the Gregorian dates of [original] in its own year and the following
     * [years] - 1 Hebrew years.
     *
     * Adar I falls back to Adar in common years; a date from a common-year Adar stays
     * in Adar (= Adar II) in leap years; a 30th missing in a 29-day month becomes the 29th.
     */
    fun dates(original: HebrewDate, years: Int): List<LocalDate> =
        (original.year until original.year + years).map { inYear(original, it) }

    private fun inYear(original: HebrewDate, year: Int): LocalDate {
        val month = if (original.month == HebrewDate.ADAR_I && !HebrewDate.isLeapYear(year)) {
            HebrewDate.ADAR
        } else {
            original.month
        }
        val day = minOf(original.day, HebrewDate.monthLength(year, month))
        return HebrewDate.toLocalDate(year, month, day)
    }
}
