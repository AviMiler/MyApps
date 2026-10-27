package com.myappstore.hebrewcalendar.theme

import android.annotation.SuppressLint
import android.content.Context

/**
 * Reads the system's wallpaper-derived tonal palettes (Android 12+), e.g.
 * `@android:color/system_accent1_600`. Looked up by name to keep one code path
 * for all 13 shades of the 5 palettes.
 */
class SystemPalette(private val context: Context) {

    fun accent1(shade: Int): Int = color("accent1", shade)
    fun accent2(shade: Int): Int = color("accent2", shade)
    fun accent3(shade: Int): Int = color("accent3", shade)
    fun neutral1(shade: Int): Int = color("neutral1", shade)
    fun neutral2(shade: Int): Int = color("neutral2", shade)

    @SuppressLint("DiscouragedApi")
    private fun color(palette: String, shade: Int): Int {
        val id = context.resources.getIdentifier("system_${palette}_$shade", "color", "android")
        require(id != 0) { "No system color $palette $shade (needs Android 12+)" }
        return context.getColor(id)
    }
}
