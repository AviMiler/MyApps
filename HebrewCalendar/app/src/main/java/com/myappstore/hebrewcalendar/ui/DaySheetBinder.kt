package com.myappstore.hebrewcalendar.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.hebrew.DateLabels
import com.myappstore.hebrewcalendar.hebrew.HebrewDate
import com.myappstore.hebrewcalendar.hebrew.JewishHolidays
import com.myappstore.hebrewcalendar.model.CalendarEvent
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.theme.Ui.cardColor
import com.myappstore.hebrewcalendar.theme.Ui.textSp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The bottom sheet under the grid: the selected day's dates, holidays and events
 * as a Material 3 list (grouped items with large outer and small inner corners).
 */
class DaySheetBinder(private val activity: Activity, private val onEventClick: (CalendarEvent) -> Unit) {

    private val sheet = activity.findViewById<LinearLayout>(R.id.sheet)
    private val title = activity.findViewById<TextView>(R.id.sheet_title)
    private val subtitle = activity.findViewById<TextView>(R.id.sheet_subtitle)
    private val badge = activity.findViewById<TextView>(R.id.sheet_badge)
    private val holidays = activity.findViewById<TextView>(R.id.sheet_holidays)
    private val events = activity.findViewById<LinearLayout>(R.id.events_container)

    fun bind(date: LocalDate, dayEvents: List<CalendarEvent>, canReadCalendar: Boolean, scheme: ColorScheme, scale: Float) {
        sheet.background = Ui.rounded(activity, scheme.surfaceContainerLow, 28f, 28f, 0f, 0f)
        activity.findViewById<View>(R.id.sheet_handle).background = Ui.rounded(activity, scheme.outline, 2f)
        title.text = DateLabels.fullHebrewDate(HebrewDate.of(date))
        title.setTextColor(scheme.onSurface)
        title.textSp(22f, scale)
        subtitle.text = DateLabels.fullGregorianDate(date)
        subtitle.setTextColor(scheme.onSurfaceVariant)
        subtitle.textSp(14f, scale)
        badge.visibility = if (date == LocalDate.now()) View.VISIBLE else View.GONE
        badge.setTextColor(scheme.onPrimaryContainer)
        badge.background = Ui.rounded(activity, scheme.primaryContainer, 8f)

        val names = JewishHolidays.between(date, date)[date].orEmpty().joinToString(" · ") { it.name }
        holidays.text = names
        holidays.visibility = if (names.isEmpty()) View.GONE else View.VISIBLE
        holidays.setTextColor(scheme.tertiary)
        holidays.textSp(14f, scale)

        events.removeAllViews()
        if (canReadCalendar && dayEvents.isEmpty()) {
            events.addView(TextView(activity).apply {
                setText(R.string.no_events)
                setTextColor(scheme.onSurfaceVariant)
                textSp(14f, scale)
                setPadding(Ui.dp(activity, 4f), Ui.dp(activity, 8f), 0, 0)
            })
        }
        dayEvents.forEachIndexed { index, event ->
            val params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            params.topMargin = Ui.dp(activity, 2f)
            events.addView(eventItem(event, index, dayEvents.size, scheme, scale), params)
        }
    }

    private fun eventItem(event: CalendarEvent, index: Int, count: Int, scheme: ColorScheme, scale: Float): View {
        val top = if (index == 0) 20f else 4f
        val bottom = if (index == count - 1) 20f else 4f
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(activity, 56f)
            setPadding(Ui.dp(activity, 16f), Ui.dp(activity, 8f), Ui.dp(activity, 16f), Ui.dp(activity, 8f))
            background = Ui.rounded(activity, scheme.cardColor, top, top, bottom, bottom)
            setOnClickListener { onEventClick(event) }
        }
        val dot = View(activity).apply { background = Ui.rounded(activity, event.color, 6f) }
        item.addView(dot, LinearLayout.LayoutParams(Ui.dp(activity, 12f), Ui.dp(activity, 12f)))
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(activity).apply {
            text = event.title
            setTextColor(scheme.onSurface)
            textSp(16f, scale)
        })
        texts.addView(TextView(activity).apply {
            text = listOf(timeRange(event), event.location).filter { it.isNotBlank() }.joinToString(" · ")
            setTextColor(scheme.onSurfaceVariant)
            textSp(13f, scale)
        })
        val params = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        params.marginStart = Ui.dp(activity, 16f)
        item.addView(texts, params)
        item.addView(ImageView(activity).apply {
            setImageDrawable(Ui.icon(activity, R.drawable.ic_chevron_left, scheme.onSurfaceVariant))
        })
        return item
    }

    private fun timeRange(event: CalendarEvent): String {
        if (event.isAllDay) return activity.getString(R.string.all_day)
        val zone = ZoneId.systemDefault()
        fun format(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).format(TIME_FORMAT)
        return "${format(event.beginMillis)} – ${format(event.endMillis)}"
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
