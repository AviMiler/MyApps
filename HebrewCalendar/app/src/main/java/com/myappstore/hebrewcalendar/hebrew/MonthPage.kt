package com.myappstore.hebrewcalendar.hebrew

import java.time.LocalDate

/** Which calendar defines the month boundaries of the grid. */
enum class ViewMode { HEBREW, GREGORIAN }

/**
 * One month of the grid, shared by the app screen and the home-screen widget.
 * In [ViewMode.HEBREW] a page runs from 1 Hebrew month to its end; in
 * [ViewMode.GREGORIAN] it is an ordinary civil month.
 */
data class MonthPage(val mode: ViewMode, val firstDay: LocalDate, val lastDay: LocalDate) {

    /** The month title, e.g. "חשוון תשפ״ז" or "אוקטובר 2026". */
    val title: String
        get() = when (mode) {
            ViewMode.HEBREW -> hebrewMonthLabel(HebrewDate.of(firstDay))
            ViewMode.GREGORIAN -> gregorianMonthLabel(firstDay)
        }

    /** The other calendar's months covered by this page, e.g. "אוקטובר – נובמבר 2026". */
    val subtitle: String
        get() = when (mode) {
            ViewMode.HEBREW -> span(gregorianMonthLabel(firstDay), gregorianMonthLabel(lastDay))
            ViewMode.GREGORIAN ->
                span(hebrewMonthLabel(HebrewDate.of(firstDay)), hebrewMonthLabel(HebrewDate.of(lastDay)))
        }

    fun next(): MonthPage = containing(lastDay.plusDays(1), mode)

    fun previous(): MonthPage = containing(firstDay.minusDays(1), mode)

    /** @return [offset] months away (negative = backwards). */
    fun shiftedBy(offset: Int): MonthPage {
        var page = this
        repeat(kotlin.math.abs(offset)) { page = if (offset > 0) page.next() else page.previous() }
        return page
    }

    operator fun contains(date: LocalDate): Boolean = date in firstDay..lastDay

    /**
     * @return Sunday-first week rows: leading/trailing nulls pad partial weeks,
     * so every row has exactly 7 cells.
     */
    fun weeks(): List<List<LocalDate?>> {
        val days = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { it <= lastDay }.toList()
        val leading = List(DateLabels.columnOf(firstDay)) { null }
        val cells: List<LocalDate?> = leading + days
        return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
    }

    companion object {
        /** @return the page of [mode] that contains [date]. */
        fun containing(date: LocalDate, mode: ViewMode): MonthPage = when (mode) {
            ViewMode.HEBREW -> {
                val hebrew = HebrewDate.of(date)
                val first = date.minusDays(hebrew.day - 1L)
                MonthPage(mode, first, first.plusDays(HebrewDate.monthLength(hebrew.year, hebrew.month) - 1L))
            }
            ViewMode.GREGORIAN -> MonthPage(mode, date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()))
        }

        private fun hebrewMonthLabel(date: HebrewDate): String =
            "${DateLabels.hebrewMonthName(date.year, date.month)} ${HebrewNumerals.formatYear(date.year)}"

        private fun gregorianMonthLabel(date: LocalDate): String =
            "${DateLabels.gregorianMonthName(date.monthValue)} ${date.year}"

        /** Joins two month labels, sharing the year when both have the same one. */
        private fun span(start: String, end: String): String {
            if (start == end) return start
            val startYear = start.substringAfterLast(' ')
            return if (startYear == end.substringAfterLast(' ')) {
                "${start.substringBeforeLast(' ')} – $end"
            } else {
                "$start – $end"
            }
        }
    }
}
