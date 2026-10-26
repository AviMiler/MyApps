package com.myappstore.hebrewcalendar.ui

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.model.DayCell

/** Visual rules for a day cell, shared by the app grid and the widget. */
object CellStyle {

    /** Up to three dots for events, then the holiday name (if any). */
    fun markerText(cell: DayCell): String {
        val dots = "•".repeat(minOf(cell.eventCount, 3))
        return listOfNotNull(dots.ifEmpty { null }, cell.holiday?.name).joinToString(" ")
    }

    @ColorRes
    fun primaryColor(cell: DayCell): Int = if (cell.isRestDay) R.color.rest_day else R.color.text_primary

    /** @return a background drawable, or 0 for none. */
    @DrawableRes
    fun background(cell: DayCell, isSelected: Boolean): Int = when {
        cell.isToday && isSelected -> R.drawable.cell_today_selected
        cell.isToday -> R.drawable.cell_today
        isSelected -> R.drawable.cell_selected
        else -> 0
    }
}
