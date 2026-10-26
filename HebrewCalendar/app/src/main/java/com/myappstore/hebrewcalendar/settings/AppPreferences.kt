package com.myappstore.hebrewcalendar.settings

import android.content.Context
import com.myappstore.hebrewcalendar.hebrew.ViewMode

/** Small persisted settings shared by the app screen and the widget. */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("hebrew_calendar", Context.MODE_PRIVATE)

    /** Whether month pages follow Hebrew or Gregorian months (app and widget alike). */
    var viewMode: ViewMode
        get() = ViewMode.values().firstOrNull { it.name == prefs.getString(KEY_VIEW_MODE, null) }
            ?: ViewMode.HEBREW
        set(value) = prefs.edit().putString(KEY_VIEW_MODE, value.name).apply()

    /** Months the widget is scrolled away from the current month; 0 = this month. */
    fun widgetOffset(widgetId: Int): Int = prefs.getInt(widgetKey(widgetId), 0)

    fun setWidgetOffset(widgetId: Int, offset: Int) =
        prefs.edit().putInt(widgetKey(widgetId), offset).apply()

    fun removeWidget(widgetId: Int) = prefs.edit().remove(widgetKey(widgetId)).apply()

    private fun widgetKey(widgetId: Int) = "widget_offset_$widgetId"

    private companion object {
        const val KEY_VIEW_MODE = "view_mode"
    }
}
