package com.myappstore.hebrewcalendar.model

/**
 * One occurrence of an event from the device calendar provider (which is where
 * Android's sync adapter keeps the user's Google Calendar).
 *
 * All-day events are stored by the provider as UTC midnights, so [beginMillis]
 * and [endMillis] must be read in UTC for them; see [EventDays].
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val color: Int,
    val calendarName: String
)

/** A calendar the user may add events to, e.g. their primary Google calendar. */
data class WritableCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isGoogle: Boolean
) {
    override fun toString(): String =
        if (displayName == accountName) displayName else "$displayName ($accountName)"
}
