package com.myappstore.hebrewcalendar.model

import com.myappstore.hebrewcalendar.hebrew.Holiday
import com.myappstore.hebrewcalendar.hebrew.HebrewDate
import com.myappstore.hebrewcalendar.hebrew.HebrewNumerals
import com.myappstore.hebrewcalendar.hebrew.JewishHolidays
import com.myappstore.hebrewcalendar.hebrew.MonthPage
import com.myappstore.hebrewcalendar.hebrew.ViewMode
import java.time.DayOfWeek
import java.time.LocalDate

/** Everything a grid cell shows; shared by the app screen and the widget. */
data class DayCell(
    val date: LocalDate,
    /** The day number in the page's calendar, e.g. "ט״ו" (Hebrew view) or "26". */
    val primaryLabel: String,
    /** The day number in the other calendar. */
    val secondaryLabel: String,
    val holiday: Holiday?,
    val isToday: Boolean,
    /** Shabbat or a Yom Tov - drawn in the rest-day color. */
    val isRestDay: Boolean,
    /** This day's events, all-day first (see EventDays). */
    val events: List<CalendarEvent>
)

/** Builds the cell rows of a [MonthPage]. */
object MonthModel {

    /** @return Sunday-first weeks; null entries are padding outside the month. */
    fun build(page: MonthPage, today: LocalDate, events: Map<LocalDate, List<CalendarEvent>>): List<List<DayCell?>> {
        val holidays = JewishHolidays.between(page.firstDay, page.lastDay)
        return page.weeks().map { week ->
            week.map { date -> date?.let { cellFor(it, page.mode, today, holidays[it].orEmpty(), events[it].orEmpty()) } }
        }
    }

    private fun cellFor(
        date: LocalDate,
        mode: ViewMode,
        today: LocalDate,
        holidays: List<Holiday>,
        events: List<CalendarEvent>
    ): DayCell {
        val hebrewDay = HebrewNumerals.format(HebrewDate.of(date).day)
        val gregorianDay = date.dayOfMonth.toString()
        val isHebrewView = mode == ViewMode.HEBREW
        return DayCell(
            date = date,
            primaryLabel = if (isHebrewView) hebrewDay else gregorianDay,
            secondaryLabel = if (isHebrewView) gregorianDay else hebrewDay,
            holiday = holidays.firstOrNull { it.isYomTov } ?: holidays.firstOrNull(),
            isToday = date == today,
            isRestDay = date.dayOfWeek == DayOfWeek.SATURDAY || holidays.any { it.isYomTov },
            events = events
        )
    }
}
