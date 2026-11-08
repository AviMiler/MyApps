package com.myappstore.hebrewcalendar.ui.components

import android.app.Activity
import android.app.AlertDialog
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.DatePicker
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.hebrew.DateLabels
import com.myappstore.hebrewcalendar.hebrew.HebrewDate
import com.myappstore.hebrewcalendar.hebrew.HebrewNumerals
import com.myappstore.hebrewcalendar.hebrew.ViewMode
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.ui.SystemBars
import java.time.LocalDate

/**
 * Date picker with a Hebrew and a Gregorian page, switched by the same kind of
 * button group used elsewhere. Opens on [initialMode].
 */
class DatePickerDialogs(private val activity: Activity, private val scheme: ColorScheme) {

    fun show(initial: LocalDate, initialMode: ViewMode, onPick: (LocalDate) -> Unit) {
        val themed = ContextThemeWrapper(activity, SystemBars.dialogTheme(scheme))
        var mode = initialMode
        var selected = initial

        val gregorian = DatePicker(themed).apply {
            calendarViewShown = false
            spinnersShown = true
            init(initial.year, initial.monthValue - 1, initial.dayOfMonth) { _, y, m, d -> selected = LocalDate.of(y, m + 1, d) }
        }
        val hebrew = HebrewPicker(themed, HebrewDate.of(initial)) { selected = it }

        val modes = listOf(ViewMode.HEBREW, ViewMode.GREGORIAN)
        val switcher = LinearLayout(themed)
        val body = FrameLayout(themed)
        fun showMode(newMode: ViewMode) {
            mode = newMode
            body.removeAllViews()
            if (mode == ViewMode.HEBREW) {
                hebrew.set(HebrewDate.of(selected))
                body.addView(hebrew.view)
            } else {
                gregorian.updateDate(selected.year, selected.monthValue - 1, selected.dayOfMonth)
                body.addView(gregorian)
            }
            SegmentedButtons.bind(
                switcher,
                listOf(activity.getString(R.string.mode_hebrew), activity.getString(R.string.mode_gregorian)),
                modes.indexOf(mode), scheme
            ) { showMode(modes[it]) }
        }
        showMode(mode)

        val content = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(activity, 20f), Ui.dp(activity, 8f), Ui.dp(activity, 20f), 0)
            addView(switcher, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 8f)
            })
        }
        AlertDialog.Builder(activity, SystemBars.dialogTheme(scheme))
            .setTitle(R.string.pick_date)
            .setView(content)
            .setPositiveButton(R.string.choose) { _, _ -> onPick(selected) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Day / month / year wheels in Hebrew letters; the months follow leap years. */
    private class HebrewPicker(context: android.content.Context, start: HebrewDate, private val onChange: (LocalDate) -> Unit) {
        private val day = NumberPicker(context)
        private val month = NumberPicker(context)
        private val year = NumberPicker(context)
        private var months: List<Int> = emptyList()
        private var updating = false

        val view: View = LinearLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
            // RTL: day on the right, then month, then year.
            addView(day, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(month, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
            addView(year, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
        }

        init {
            year.minValue = MIN_YEAR
            year.maxValue = MAX_YEAR
            year.setFormatter { HebrewNumerals.formatYear(it) }
            day.setFormatter { HebrewNumerals.format(it) }
            listOf(day, month, year).forEach { it.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS }
            day.setOnValueChangedListener { _, _, _ -> changed() }
            month.setOnValueChangedListener { _, _, _ -> changed() }
            year.setOnValueChangedListener { _, _, _ -> changed(yearChanged = true) }
            set(start)
        }

        fun set(date: HebrewDate) {
            updating = true
            year.value = date.year.coerceIn(MIN_YEAR, MAX_YEAR)
            fillMonths(date.year, date.month)
            fillDays(date.year, date.month, date.day)
            updating = false
        }

        private fun changed(yearChanged: Boolean = false) {
            if (updating) return
            updating = true
            val oldMonth = months[month.value]
            if (yearChanged) fillMonths(year.value, oldMonth)
            fillDays(year.value, months[month.value], day.value)
            updating = false
            onChange(HebrewDate.toLocalDate(year.value, months[month.value], day.value))
        }

        /** Rebuilds the month wheel for [y], keeping [wanted] (Adar I falls back to Adar). */
        private fun fillMonths(y: Int, wanted: Int) {
            months = HebrewDate.monthsOfYear(y)
            val names = months.map { DateLabels.hebrewMonthName(y, it) }.toTypedArray()
            month.displayedValues = null
            month.minValue = 0
            month.maxValue = months.lastIndex
            month.displayedValues = names
            month.value = months.indexOf(wanted).takeIf { it >= 0 } ?: months.indexOf(HebrewDate.ADAR).coerceAtLeast(0)
        }

        private fun fillDays(y: Int, m: Int, wanted: Int) {
            val length = HebrewDate.monthLength(y, m)
            day.minValue = 1
            day.maxValue = length
            day.value = wanted.coerceIn(1, length)
        }

        private companion object {
            const val MIN_YEAR = 5700
            const val MAX_YEAR = 5900
        }
    }
}
