package com.myappstore.hebrewcalendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.myappstore.hebrewcalendar.hebrew.ViewMode
import com.myappstore.hebrewcalendar.settings.AppPreferences

/**
 * The monthly home-screen widget. Each widget instance remembers how many months
 * it was scrolled with its arrows; tapping the title returns to the current month.
 * The mode button switches Hebrew/Gregorian months for the app and all widgets.
 */
class MonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { WidgetRenderer.render(context, manager, it) }
        WidgetRefreshScheduler.schedule(context)
    }

    /** Resizing changes how many event titles fit in a day. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle) =
        WidgetRenderer.render(context, manager, widgetId)

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        val preferences = AppPreferences(context)
        widgetIds.forEach(preferences::removeWidget)
    }

    override fun onDisabled(context: Context) = WidgetRefreshScheduler.cancel(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREVIOUS, ACTION_NEXT, ACTION_TODAY -> navigate(context, intent)
            ACTION_TOGGLE_MODE -> toggleMode(context)
            ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> refreshAll(context)
        }
    }

    private fun navigate(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val preferences = AppPreferences(context)
        val offset = when (intent.action) {
            ACTION_PREVIOUS -> preferences.widgetOffset(widgetId) - 1
            ACTION_NEXT -> preferences.widgetOffset(widgetId) + 1
            else -> 0
        }
        preferences.setWidgetOffset(widgetId, offset)
        WidgetRenderer.render(context, AppWidgetManager.getInstance(context), widgetId)
    }

    /** Month offsets count months of the old mode, so every widget goes back to the current month. */
    private fun toggleMode(context: Context) {
        val preferences = AppPreferences(context)
        preferences.viewMode = if (preferences.viewMode == ViewMode.HEBREW) ViewMode.GREGORIAN else ViewMode.HEBREW
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, MonthWidgetProvider::class.java)).forEach { preferences.setWidgetOffset(it, 0) }
        refreshAll(context)
    }

    companion object {
        const val ACTION_PREVIOUS = "com.myappstore.hebrewcalendar.widget.PREVIOUS"
        const val ACTION_NEXT = "com.myappstore.hebrewcalendar.widget.NEXT"
        const val ACTION_TODAY = "com.myappstore.hebrewcalendar.widget.TODAY"
        const val ACTION_TOGGLE_MODE = "com.myappstore.hebrewcalendar.widget.TOGGLE_MODE"
        const val ACTION_REFRESH = "com.myappstore.hebrewcalendar.widget.REFRESH"

        /** Redraws every placed widget, e.g. after events or settings changed. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(ComponentName(context, MonthWidgetProvider::class.java))
            widgetIds.forEach { WidgetRenderer.render(context, manager, it) }
            if (widgetIds.isNotEmpty()) WidgetRefreshScheduler.schedule(context)
        }

        /** @return a broadcast back to this provider; the data URI keeps each widget's intents distinct. */
        fun broadcast(context: Context, action: String, widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): PendingIntent {
            val intent = Intent(context, MonthWidgetProvider::class.java)
                .setAction(action)
                .setData(Uri.parse("hebrewcalendar://widget/$widgetId/$action"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            return PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
