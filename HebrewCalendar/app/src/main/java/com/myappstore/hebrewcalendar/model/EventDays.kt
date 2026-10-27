package com.myappstore.hebrewcalendar.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Spreads calendar events over the days they cover. */
object EventDays {

    /**
     * @return events per day for [first]..[last]; multi-day events appear on each
     * day they cover. Each day lists all-day events first, then by start time.
     */
    fun groupByDay(
        events: List<CalendarEvent>,
        zone: ZoneId,
        first: LocalDate,
        last: LocalDate
    ): Map<LocalDate, List<CalendarEvent>> {
        val result = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
        for (event in events) {
            val (start, end) = daysOf(event, zone)
            var day = maxOf(start, first)
            while (day <= minOf(end, last)) {
                result.getOrPut(day) { mutableListOf() }.add(event)
                day = day.plusDays(1)
            }
        }
        val order = compareBy<CalendarEvent>({ !it.isAllDay }, { it.beginMillis })
        return result.mapValues { (_, dayEvents) -> dayEvents.sortedWith(order) }
    }

    /** @return the first and last civil day [event] occupies. */
    fun daysOf(event: CalendarEvent, zone: ZoneId): Pair<LocalDate, LocalDate> {
        val eventZone = if (event.isAllDay) ZoneOffset.UTC else zone
        val start = Instant.ofEpochMilli(event.beginMillis).atZone(eventZone).toLocalDate()
        // The end is exclusive: an event ending exactly at midnight does not touch that day.
        val lastMillis = maxOf(event.beginMillis, event.endMillis - 1)
        val end = Instant.ofEpochMilli(lastMillis).atZone(eventZone).toLocalDate()
        return start to end
    }
}
