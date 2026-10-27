package com.myappstore.hebrewcalendar.settings

/** Color source: the system wallpaper palette (Android 12+) or a fixed palette. */
enum class Palette { WALLPAPER, BLUE, GREEN, TERRACOTTA, VIOLET, TEAL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How today's date is marked in the grid. */
enum class TodayShape { COOKIE, CIRCLE, SQUARE, UNDERLINE }

/** The user's display customization, shared by the app and the widget. */
data class DisplaySettings(
    val palette: Palette = Palette.WALLPAPER,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val todayShape: TodayShape = TodayShape.COOKIE,
    val showSecondaryDate: Boolean = true,
    /** Event titles inside day cells (like Google Calendar); otherwise colored dots. */
    val showEventTitles: Boolean = true,
    val showHolidays: Boolean = true,
    val showGridLines: Boolean = true,
    /** Index into [TEXT_SCALES]. */
    val textScaleIndex: Int = 1
) {
    val textScale: Float get() = TEXT_SCALES[textScaleIndex.coerceIn(TEXT_SCALES.indices)]

    companion object {
        val TEXT_SCALES = listOf(0.9f, 1f, 1.1f, 1.2f, 1.3f)
    }
}
