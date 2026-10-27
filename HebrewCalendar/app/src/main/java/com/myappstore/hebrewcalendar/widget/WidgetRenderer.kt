package com.myappstore.hebrewcalendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.calendar.CalendarRepository
import com.myappstore.hebrewcalendar.hebrew.MonthPage
import com.myappstore.hebrewcalendar.hebrew.ViewMode
import com.myappstore.hebrewcalendar.model.DayCell
import com.myappstore.hebrewcalendar.model.MonthModel
import com.myappstore.hebrewcalendar.settings.AppPreferences
import com.myappstore.hebrewcalendar.settings.DisplaySettings
import com.myappstore.hebrewcalendar.settings.TodayShape
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.ui.HolidayNames
import com.myappstore.hebrewcalendar.ui.MainActivity
import java.time.LocalDate

/**
 * Builds the widget's RemoteViews in the app's Material 3 colors. The grid is
 * nested RemoteViews (a row layout per week, a cell layout per day), so no
 * RemoteViewsService is needed and every day can open the app on itself.
 * Like the app grid, cells show the holiday and event titles as small chips, as
 * many as fit the widget's current height.
 */
object WidgetRenderer {

    private val WEEKDAY_IDS = listOf(
        R.id.widget_wd_0, R.id.widget_wd_1, R.id.widget_wd_2, R.id.widget_wd_3,
        R.id.widget_wd_4, R.id.widget_wd_5, R.id.widget_wd_6
    )

    fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val preferences = AppPreferences(context)
        val settings = preferences.display
        val colors = WidgetColors(context, settings)
        val today = LocalDate.now()
        val page = MonthPage.containing(today, preferences.viewMode).shiftedBy(preferences.widgetOffset(widgetId))
        val events = CalendarRepository(context).eventsByDay(page.firstDay, page.lastDay)

        val views = RemoteViews(context.packageName, R.layout.widget_month)
        colors.tint(views, R.id.widget_bg) { it.surfaceContainer }
        views.setTextViewText(R.id.widget_title, page.title.substringBeforeLast(' '))
        views.setTextViewText(R.id.widget_year, page.title.substringAfterLast(' '))
        colors.text(views, R.id.widget_title) { it.onSurface }
        colors.text(views, R.id.widget_year) { it.onSurfaceVariant }
        listOf(R.id.widget_previous, R.id.widget_next).forEach { id -> colors.tint(views, id) { it.onSurfaceVariant } }
        colors.tint(views, R.id.widget_add_bg) { it.primary }
        colors.tint(views, R.id.widget_add_icon) { it.onPrimary }
        WEEKDAY_IDS.forEach { id -> colors.text(views, id) { it.onSurfaceVariant } }
        val toGregorian = preferences.viewMode == ViewMode.HEBREW
        views.setTextViewText(R.id.widget_mode_label, context.getString(if (toGregorian) R.string.mode_gregorian else R.string.mode_hebrew))
        views.setContentDescription(R.id.widget_mode, context.getString(if (toGregorian) R.string.switch_to_gregorian else R.string.switch_to_hebrew))
        colors.tint(views, R.id.widget_mode_bg) { it.secondaryContainer }
        colors.text(views, R.id.widget_mode_label) { it.onSecondaryContainer }

        views.setOnClickPendingIntent(R.id.widget_previous, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_PREVIOUS, widgetId))
        views.setOnClickPendingIntent(R.id.widget_next, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_NEXT, widgetId))
        views.setOnClickPendingIntent(R.id.widget_mode, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_TOGGLE_MODE, widgetId))
        views.setOnClickPendingIntent(R.id.widget_titles, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_TODAY, widgetId))
        views.setOnClickPendingIntent(R.id.widget_add, openApp(context, today, addEvent = true))

        views.removeAllViews(R.id.widget_grid)
        colors.background(views, R.id.widget_grid) { if (settings.showGridLines) it.outlineVariant else it.surfaceContainer }
        val weeks = MonthModel.build(page, today, events)
        val room = chipRoom(manager, widgetId, weeks.size)
        for (week in weeks) {
            val row = RemoteViews(context.packageName, R.layout.widget_week_row)
            week.forEach { cell -> row.addView(R.id.widget_week_row, cellViews(context, colors, settings, cell, room)) }
            views.addView(R.id.widget_grid, row)
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun cellViews(context: Context, colors: WidgetColors, settings: DisplaySettings, cell: DayCell?, room: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_cell)
        colors.background(views, R.id.widget_cell) { it.surfaceContainer }
        if (cell == null) return views
        views.setTextViewText(R.id.widget_cell_number, cell.primaryLabel)
        val isHoliday = settings.showHolidays && cell.holiday != null
        colors.text(views, R.id.widget_cell_number) {
            when {
                cell.isToday && settings.todayShape != TodayShape.UNDERLINE -> it.onPrimary
                cell.isToday -> it.primary
                cell.isRestDay -> it.error
                isHoliday -> it.tertiary
                else -> it.onSurface
            }
        }
        if (cell.isToday) {
            views.setImageViewResource(R.id.widget_cell_shape, Ui.todayShapeRes(settings.todayShape))
            views.setViewVisibility(R.id.widget_cell_shape, View.VISIBLE)
            colors.tint(views, R.id.widget_cell_shape) { it.primary }
        }
        if (settings.showSecondaryDate) {
            views.setTextViewText(R.id.widget_cell_secondary, cell.secondaryLabel)
            colors.text(views, R.id.widget_cell_secondary) { if (isHoliday) it.tertiary else it.onSurfaceVariant }
        } else {
            views.setViewVisibility(R.id.widget_cell_secondary, View.GONE)
        }
        chips(context, colors, settings, cell, room).forEach { views.addView(R.id.widget_cell_events, it) }
        val titlesShown = settings.showEventTitles && room > (if (isHoliday) 1 else 0)
        if (!titlesShown) views.setTextViewText(R.id.widget_cell_dots, eventDots(cell))
        views.setOnClickPendingIntent(R.id.widget_cell, openApp(context, cell.date, addEvent = false))
        return views
    }

    /**
     * How many chip lines fit under the day number, from the widget's current
     * height (the launcher reports it in dp; MAX_HEIGHT is the portrait height).
     */
    private fun chipRoom(manager: AppWidgetManager, widgetId: Int, weeks: Int): Int {
        val heightDp = manager.getAppWidgetOptions(widgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
        val cellDp = (heightDp - CHROME_DP).toFloat() / weeks.coerceAtLeast(1)
        return ((cellDp - NUMBER_ROW_DP) / CHIP_DP).toInt().coerceIn(0, MAX_CHIPS)
    }

    /** Holiday tag first, then event titles, with "+N" in the last line when they don't all fit. */
    private fun chips(context: Context, colors: WidgetColors, settings: DisplaySettings, cell: DayCell, room: Int): List<RemoteViews> {
        if (room == 0) return emptyList()
        val chips = mutableListOf<RemoteViews>()
        val holiday = cell.holiday?.takeIf { settings.showHolidays }
        if (holiday != null) {
            chips += chip(context, HolidayNames.short(holiday.name)) { views ->
                colors.background(views, R.id.widget_chip) { if (holiday.isYomTov) it.errorContainer else it.tertiaryContainer }
                colors.text(views, R.id.widget_chip) { if (holiday.isYomTov) it.onErrorContainer else it.onTertiaryContainer }
            }
        }
        if (!settings.showEventTitles) return chips
        val left = room - chips.size
        if (left <= 0) return chips
        // With a single free line, the first title beats a bare "+N".
        val shown = if (cell.events.size > left && left > 1) left - 1 else minOf(cell.events.size, left)
        cell.events.take(shown).forEach { event ->
            chips += chip(context, event.title) { views ->
                views.setInt(R.id.widget_chip, "setBackgroundColor", event.color)
                views.setTextColor(R.id.widget_chip, WHITE)
            }
        }
        val hidden = cell.events.size - shown
        if (hidden > 0 && left > 1) {
            chips += chip(context, "+$hidden") { views -> colors.text(views, R.id.widget_chip) { it.onSurfaceVariant } }
        }
        return chips
    }

    private fun chip(context: Context, label: String, style: (RemoteViews) -> Unit) =
        RemoteViews(context.packageName, R.layout.widget_event_chip).apply {
            setTextViewText(R.id.widget_chip, label)
            style(this)
        }

    /** Up to three dots in the events' own calendar colors. */
    private fun eventDots(cell: DayCell): CharSequence = SpannableStringBuilder().apply {
        cell.events.take(3).forEach { event ->
            val start = length
            append("●")
            setSpan(ForegroundColorSpan(event.color), start, length, 0)
        }
    }

    /** Opens the app on [date]; the data URI keeps each day's PendingIntent distinct. */
    private fun openApp(context: Context, date: LocalDate, addEvent: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("hebrewcalendar://day/$date?add=$addEvent"))
            .putExtra(MainActivity.EXTRA_DATE, date.toString())
            .putExtra(MainActivity.EXTRA_ADD_EVENT, addEvent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private val WHITE = ColorScheme.opaque("FFFFFF")

    /** Sizes in dp from widget_month / widget_day_cell, for [chipRoom]. */
    private const val DEFAULT_HEIGHT_DP = 250
    private const val CHROME_DP = 20 + 38 + 18 // vertical padding, header, weekday letters
    private const val NUMBER_ROW_DP = 23f
    private const val CHIP_DP = 12f
    private const val MAX_CHIPS = 4
}
