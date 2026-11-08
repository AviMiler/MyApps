package com.myappstore.hebrewcalendar.widget

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import com.myappstore.hebrewcalendar.settings.DisplaySettings
import com.myappstore.hebrewcalendar.settings.ThemeMode
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.ThemeController

/**
 * Applies color roles to widget views. When the theme follows the system on
 * Android 12+, both light and dark values are sent (RemoteViews.setColorInt), so
 * the launcher switches the widget with night mode without waiting for an update.
 */
class WidgetColors(context: Context, settings: DisplaySettings) {

    private val followsSystem = settings.themeMode == ThemeMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    private val light = ThemeController.scheme(context, settings, isDark = false)
    private val dark = ThemeController.scheme(context, settings, isDark = true)
    private val current = ThemeController.scheme(context, settings)

    /** Calls [method] (an int setter such as "setTextColor") on [viewId] with [role]'s color. */
    fun apply(views: RemoteViews, viewId: Int, method: String, role: (ColorScheme) -> Int) {
        if (followsSystem && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorInt(viewId, method, role(light), role(dark))
        } else {
            views.setInt(viewId, method, role(current))
        }
    }

    fun text(views: RemoteViews, viewId: Int, role: (ColorScheme) -> Int) = apply(views, viewId, "setTextColor", role)

    fun tint(views: RemoteViews, viewId: Int, role: (ColorScheme) -> Int) = apply(views, viewId, "setColorFilter", role)

    fun background(views: RemoteViews, viewId: Int, role: (ColorScheme) -> Int) =
        apply(views, viewId, "setBackgroundColor", role)
}
