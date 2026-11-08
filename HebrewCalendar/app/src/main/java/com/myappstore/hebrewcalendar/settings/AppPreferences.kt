package com.myappstore.hebrewcalendar.settings

import android.content.Context
import android.content.SharedPreferences
import com.myappstore.hebrewcalendar.hebrew.Cities
import com.myappstore.hebrewcalendar.hebrew.City
import com.myappstore.hebrewcalendar.hebrew.ViewMode

/** Small persisted settings shared by the app screen and the widget. */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("hebrew_calendar", Context.MODE_PRIVATE)

    /** Whether month pages follow Hebrew or Gregorian months (app and widget alike). */
    var viewMode: ViewMode
        get() = prefs.enum(KEY_VIEW_MODE, ViewMode.HEBREW)
        set(value) = prefs.edit().putString(KEY_VIEW_MODE, value.name).apply()

    var display: DisplaySettings
        get() {
            val defaults = DisplaySettings()
            return DisplaySettings(
                palette = prefs.enum(KEY_PALETTE, defaults.palette),
                themeMode = prefs.enum(KEY_THEME_MODE, defaults.themeMode),
                todayShape = prefs.enum(KEY_TODAY_SHAPE, defaults.todayShape),
                showSecondaryDate = prefs.getBoolean(KEY_SECONDARY_DATE, defaults.showSecondaryDate),
                showEventTitles = prefs.getBoolean(KEY_EVENT_TITLES, defaults.showEventTitles),
                showHolidays = prefs.getBoolean(KEY_HOLIDAYS, defaults.showHolidays),
                showGridLines = prefs.getBoolean(KEY_GRID_LINES, defaults.showGridLines),
                textScaleIndex = prefs.getInt(KEY_TEXT_SCALE, defaults.textScaleIndex),
                colors = ColorItem.values().filter { prefs.contains(colorKey(it)) }
                    .associateWith { prefs.getInt(colorKey(it), 0) }
            )
        }
        set(value) = prefs.edit()
            .putString(KEY_PALETTE, value.palette.name)
            .putString(KEY_THEME_MODE, value.themeMode.name)
            .putString(KEY_TODAY_SHAPE, value.todayShape.name)
            .putBoolean(KEY_SECONDARY_DATE, value.showSecondaryDate)
            .putBoolean(KEY_EVENT_TITLES, value.showEventTitles)
            .putBoolean(KEY_HOLIDAYS, value.showHolidays)
            .putBoolean(KEY_GRID_LINES, value.showGridLines)
            .putInt(KEY_TEXT_SCALE, value.textScaleIndex)
            .apply {
                ColorItem.values().forEach { item ->
                    value.colors[item]?.let { putInt(colorKey(item), it) } ?: remove(colorKey(item))
                }
            }
            .apply()

    /** The color the user gave this one event (all its occurrences), or null for the default. */
    fun eventColor(eventId: Long): Int? =
        if (prefs.contains(eventKey(eventId))) prefs.getInt(eventKey(eventId), 0) else null

    fun setEventColor(eventId: Long, color: Int?) {
        val edit = prefs.edit()
        if (color == null) edit.remove(eventKey(eventId)) else edit.putInt(eventKey(eventId), color)
        edit.apply()
    }

    /** The city the day's zmanim are computed for. */
    var zmanimCity: City
        get() = Cities.named(prefs.getString(KEY_ZMANIM_CITY, null))
        set(value) = prefs.edit().putString(KEY_ZMANIM_CITY, value.name).apply()

    /** Whether the calendar permission was requested at least once (to detect "don't ask again"). */
    var hasAskedCalendarPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_PERMISSION, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED_PERMISSION, value).apply()

    /** Months the widget is scrolled away from the current month; 0 = this month. */
    fun widgetOffset(widgetId: Int): Int = prefs.getInt(widgetKey(widgetId), 0)

    fun setWidgetOffset(widgetId: Int, offset: Int) =
        prefs.edit().putInt(widgetKey(widgetId), offset).apply()

    fun removeWidget(widgetId: Int) = prefs.edit().remove(widgetKey(widgetId)).apply()

    private fun colorKey(item: ColorItem) = "color_${item.name}"

    private fun eventKey(eventId: Long) = "event_color_$eventId"

    private fun widgetKey(widgetId: Int) = "widget_offset_$widgetId"

    private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == getString(key, null) } ?: default

    private companion object {
        const val KEY_VIEW_MODE = "view_mode"
        const val KEY_ASKED_PERMISSION = "asked_calendar_permission"
        const val KEY_PALETTE = "palette"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_TODAY_SHAPE = "today_shape"
        const val KEY_SECONDARY_DATE = "show_secondary_date"
        const val KEY_EVENT_TITLES = "show_event_titles"
        const val KEY_HOLIDAYS = "show_holidays"
        const val KEY_GRID_LINES = "show_grid_lines"
        const val KEY_TEXT_SCALE = "text_scale_index"
        const val KEY_ZMANIM_CITY = "zmanim_city"
    }
}
