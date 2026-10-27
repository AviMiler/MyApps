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
import com.myappstore.hebrewcalendar.model.DayCell
import com.myappstore.hebrewcalendar.model.MonthModel
import com.myappstore.hebrewcalendar.settings.AppPreferences
import com.myappstore.hebrewcalendar.settings.DisplaySettings
import com.myappstore.hebrewcalendar.settings.TodayShape
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.ui.MainActivity
import java.time.LocalDate

/**
 * Builds the widget's RemoteViews in the app's Material 3 colors. The grid is
 * nested RemoteViews (a row layout per week, a cell layout per day), so no
 * RemoteViewsService is needed and every day can open the app on itself.
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

        views.setOnClickPendingIntent(R.id.widget_previous, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_PREVIOUS, widgetId))
        views.setOnClickPendingIntent(R.id.widget_next, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_NEXT, widgetId))
        views.setOnClickPendingIntent(R.id.widget_titles, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_TODAY, widgetId))
        views.setOnClickPendingIntent(R.id.widget_add, openApp(context, today, addEvent = true))

        views.removeAllViews(R.id.widget_grid)
        colors.background(views, R.id.widget_grid) { if (settings.showGridLines) it.outlineVariant else it.surfaceContainer }
        for (week in MonthModel.build(page, today, events)) {
            val row = RemoteViews(context.packageName, R.layout.widget_week_row)
            week.forEach { cell -> row.addView(R.id.widget_week_row, cellViews(context, colors, settings, cell)) }
            views.addView(R.id.widget_grid, row)
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun cellViews(context: Context, colors: WidgetColors, settings: DisplaySettings, cell: DayCell?): RemoteViews {
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
        views.setTextViewText(R.id.widget_cell_dots, eventDots(cell))
        views.setOnClickPendingIntent(R.id.widget_cell, openApp(context, cell.date, addEvent = false))
        return views
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
}
