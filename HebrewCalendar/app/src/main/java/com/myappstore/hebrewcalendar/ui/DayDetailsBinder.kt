package com.myappstore.hebrewcalendar.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.hebrew.DateLabels
import com.myappstore.hebrewcalendar.hebrew.HebrewDate
import com.myappstore.hebrewcalendar.hebrew.JewishHolidays
import com.myappstore.hebrewcalendar.model.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shows the selected day's dates, holidays and Google Calendar events. */
class DayDetailsBinder(activity: Activity, private val onEventClick: (CalendarEvent) -> Unit) {

    private val hebrewDate = activity.findViewById<TextView>(R.id.text_selected_hebrew)
    private val gregorianDate = activity.findViewById<TextView>(R.id.text_selected_gregorian)
    private val holidays = activity.findViewById<TextView>(R.id.text_selected_holidays)
    private val events = activity.findViewById<LinearLayout>(R.id.events_container)
    private val inflater = activity.layoutInflater

    fun bind(date: LocalDate, dayEvents: List<CalendarEvent>, canReadCalendar: Boolean) {
        hebrewDate.text = DateLabels.fullHebrewDate(HebrewDate.of(date))
        gregorianDate.text = DateLabels.fullGregorianDate(date)
        val names = JewishHolidays.between(date, date)[date].orEmpty().joinToString(" · ") { it.name }
        holidays.text = names
        holidays.visibility = if (names.isEmpty()) View.GONE else View.VISIBLE

        events.removeAllViews()
        if (canReadCalendar && dayEvents.isEmpty()) {
            val empty = TextView(events.context).apply {
                setText(R.string.no_events)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            events.addView(empty)
        }
        dayEvents.forEach { events.addView(eventRow(it)) }
    }

    private fun eventRow(event: CalendarEvent): View {
        val row = inflater.inflate(R.layout.item_event, events, false)
        row.findViewById<View>(R.id.event_color).background.mutate().setTint(event.color)
        row.findViewById<TextView>(R.id.event_time).text = if (event.isAllDay) {
            row.context.getString(R.string.all_day)
        } else {
            Instant.ofEpochMilli(event.beginMillis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
        }
        row.findViewById<TextView>(R.id.event_title).text = event.title
        row.findViewById<TextView>(R.id.event_calendar).text = event.calendarName
        row.setOnClickListener { onEventClick(event) }
        return row
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
