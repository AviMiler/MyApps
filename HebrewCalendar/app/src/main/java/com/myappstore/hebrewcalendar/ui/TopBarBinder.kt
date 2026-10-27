package com.myappstore.hebrewcalendar.ui

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.hebrew.MonthPage
import com.myappstore.hebrewcalendar.hebrew.ViewMode
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.ui.components.SegmentedButtons
import java.time.LocalDate

/** Top app bar, month subtitle, Hebrew/Gregorian button group and the FAB. */
class TopBarBinder(private val activity: Activity) {

    fun bind(page: MonthPage, scheme: ColorScheme, onModeSelected: (ViewMode) -> Unit) {
        val (month, year) = page.title.substringBeforeLast(' ') to page.title.substringAfterLast(' ')
        text(R.id.title_month, month, scheme.onSurface).typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        text(R.id.title_year, year, scheme.onSurfaceVariant)
        text(R.id.subtitle, page.subtitle, scheme.onSurfaceVariant)
        listOf(R.id.title_dropdown, R.id.button_sync, R.id.button_settings, R.id.button_previous, R.id.button_next)
            .forEach { activity.findViewById<ImageView>(it).setColorFilter(scheme.onSurfaceVariant) }
        bindTodayIcon(scheme)

        val modes = listOf(ViewMode.HEBREW, ViewMode.GREGORIAN)
        SegmentedButtons.bind(
            activity.findViewById(R.id.mode_group),
            listOf(activity.getString(R.string.mode_hebrew), activity.getString(R.string.mode_gregorian)),
            modes.indexOf(page.mode),
            scheme
        ) { onModeSelected(modes[it]) }

        activity.findViewById<TextView>(R.id.fab).apply {
            setTextColor(scheme.onPrimaryContainer)
            background = Ui.rounded(activity, scheme.primaryContainer, 16f)
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                Ui.icon(activity, R.drawable.ic_add, scheme.onPrimaryContainer), null, null, null
            )
        }
    }

    /** Google Calendar style "today" action: a small calendar page with today's number. */
    private fun bindTodayIcon(scheme: ColorScheme) {
        val stroke = Ui.dp(activity, 2f)
        val frame = GradientDrawable().apply {
            setStroke(stroke, scheme.onSurfaceVariant)
            cornerRadius = Ui.dp(activity, 4f).toFloat()
        }
        val header = GradientDrawable().apply {
            setColor(scheme.onSurfaceVariant)
            cornerRadii = Ui.dp(activity, 4f).toFloat().let { floatArrayOf(it, it, it, it, 0f, 0f, 0f, 0f) }
        }
        val icon = LayerDrawable(arrayOf(frame, header)).apply {
            setLayerHeight(1, Ui.dp(activity, 5f))
            setLayerGravity(1, Gravity.TOP)
        }
        text(R.id.today_icon, LocalDate.now().dayOfMonth.toString(), scheme.onSurfaceVariant).background = icon
    }

    private fun text(id: Int, value: String, color: Int): TextView =
        activity.findViewById<TextView>(id).apply {
            text = value
            setTextColor(color)
        }
}
