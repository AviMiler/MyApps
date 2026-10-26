package com.myappstore.hebrewcalendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.calendar.CalendarRepository
import com.myappstore.hebrewcalendar.hebrew.MonthPage
import com.myappstore.hebrewcalendar.model.DayCell
import com.myappstore.hebrewcalendar.model.MonthModel
import com.myappstore.hebrewcalendar.settings.AppPreferences
import com.myappstore.hebrewcalendar.ui.CellStyle
import com.myappstore.hebrewcalendar.ui.MainActivity
import java.time.LocalDate

/**
 * Builds the widget's RemoteViews. The grid is assembled from nested RemoteViews
 * (one row layout per week, one cell layout per day) so no RemoteViewsService
 * or collection adapter is needed.
 */
object WidgetRenderer {

    fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val preferences = AppPreferences(context)
        val today = LocalDate.now()
        val page = MonthPage.containing(today, preferences.viewMode).shiftedBy(preferences.widgetOffset(widgetId))
        val eventCounts = CalendarRepository(context).eventsByDay(page.firstDay, page.lastDay).mapValues { it.value.size }

        val views = RemoteViews(context.packageName, R.layout.widget_month)
        views.setTextViewText(R.id.widget_title, page.title)
        views.setTextViewText(R.id.widget_subtitle, page.subtitle)
        views.setOnClickPendingIntent(
            R.id.widget_previous, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_PREVIOUS, widgetId)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_NEXT, widgetId)
        )
        views.setOnClickPendingIntent(
            R.id.widget_titles, MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_TODAY, widgetId)
        )

        views.removeAllViews(R.id.widget_grid)
        for (week in MonthModel.build(page, today, eventCounts)) {
            val row = RemoteViews(context.packageName, R.layout.widget_week_row)
            week.forEach { cell -> row.addView(R.id.widget_week_row, cellViews(context, cell)) }
            views.addView(R.id.widget_grid, row)
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun cellViews(context: Context, cell: DayCell?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day_cell)
        if (cell == null) return views
        views.setTextViewText(R.id.widget_cell_primary, cell.primaryLabel)
        views.setTextColor(R.id.widget_cell_primary, ContextCompat.getColor(context, CellStyle.primaryColor(cell)))
        views.setTextViewText(R.id.widget_cell_secondary, cell.secondaryLabel)
        views.setTextViewText(R.id.widget_cell_marker, CellStyle.markerText(cell))
        val background = CellStyle.background(cell, isSelected = false)
        if (background != 0) views.setInt(R.id.widget_cell, "setBackgroundResource", background)
        views.setOnClickPendingIntent(R.id.widget_cell, openDay(context, cell.date))
        return views
    }

    /** Opens the app on [date]; the data URI makes each day's PendingIntent distinct. */
    private fun openDay(context: Context, date: LocalDate): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("hebrewcalendar://day/$date"))
            .putExtra(MainActivity.EXTRA_DATE, date.toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
