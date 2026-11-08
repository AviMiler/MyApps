package com.myappstore.hebrewcalendar.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import com.myappstore.hebrewcalendar.theme.ColorScheme

/** Colors the status and navigation bars to match the screen's surface. */
object SystemBars {

    fun apply(activity: Activity, scheme: ColorScheme, navigationColor: Int = scheme.surfaceContainerLow) {
        val window = activity.window
        window.statusBarColor = scheme.surface
        window.navigationBarColor = navigationColor
        window.decorView.setBackgroundColor(scheme.surface)
        val lightIcons = !scheme.isDark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (lightIcons) flags else 0, flags)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (lightIcons) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                0
            }
        }
    }

    /** Framework dialog theme matching light/dark (DeviceDefault = the phone's native look). */
    fun dialogTheme(scheme: ColorScheme): Int = if (scheme.isDark) {
        android.R.style.Theme_DeviceDefault_Dialog_Alert
    } else {
        android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
    }
}
