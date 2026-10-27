package com.myappstore.hebrewcalendar.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import com.myappstore.hebrewcalendar.hebrew.DateLabels
import com.myappstore.hebrewcalendar.model.DayCell
import com.myappstore.hebrewcalendar.settings.DisplaySettings
import com.myappstore.hebrewcalendar.settings.TodayShape
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.theme.Ui.textSp
import java.time.LocalDate

/**
 * Draws the app's month grid in the style of Google Calendar's month view: day
 * number at the top, holiday tag and event titles as small colored chips.
 * Thin lines come from the grid's background showing through 0.5dp cell margins.
 */
class MonthGridRenderer(
    private val context: Context,
    private val scheme: ColorScheme,
    private val settings: DisplaySettings
) {
    private val scale = settings.textScale

    fun bindWeekdays(header: LinearLayout) {
        header.removeAllViews()
        DateLabels.WEEKDAY_LETTERS.forEach { letter ->
            val label = TextView(context).apply {
                text = letter
                gravity = Gravity.CENTER
                textSp(11f, scale)
                setTextColor(scheme.onSurfaceVariant)
            }
            header.addView(label, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Replaces [grid]'s rows with [weeks]; [selected] gets the container color. */
    fun bindGrid(grid: LinearLayout, weeks: List<List<DayCell?>>, selected: LocalDate?, onDayClick: ((LocalDate) -> Unit)?) {
        grid.removeAllViews()
        grid.setBackgroundColor(if (settings.showGridLines) scheme.outlineVariant else scheme.surface)
        val half = Ui.dp(context, 0.5f).coerceAtLeast(1)
        for (week in weeks) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (cell in week) {
                val params = LinearLayout.LayoutParams(0, Ui.dp(context, 58f * scale), 1f)
                params.setMargins(0, half, half, 0)
                row.addView(cellView(cell, cell?.date == selected, onDayClick), params)
            }
            grid.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun cellView(cell: DayCell?, isSelected: Boolean, onDayClick: ((LocalDate) -> Unit)?): View {
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 2f), Ui.dp(context, 2f), Ui.dp(context, 2f), 0)
            setBackgroundColor(if (isSelected) scheme.primaryContainer else scheme.surface)
        }
        if (cell == null) return view
        view.addView(numberRow(cell, isSelected))
        chips(cell).forEach { view.addView(it, chipParams()) }
        view.contentDescription = DateLabels.fullGregorianDate(cell.date)
        onDayClick?.let { click -> view.setOnClickListener { click(cell.date) } }
        return view
    }

    private fun numberRow(cell: DayCell, isSelected: Boolean): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val size = Ui.dp(context, 24f * scale)
        val number = TextView(context).apply {
            text = cell.primaryLabel
            gravity = Gravity.CENTER
            textSp(12.5f, scale)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(numberColor(cell, isSelected))
            if (cell.isToday) background = Ui.icon(context, Ui.todayShapeRes(settings.todayShape), scheme.primary)
        }
        row.addView(number, LinearLayout.LayoutParams(size, size))
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        if (settings.showSecondaryDate) {
            val secondary = TextView(context).apply {
                text = cell.secondaryLabel
                textSp(9f, scale)
                setTextColor(if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
            }
            row.addView(secondary)
        }
        return row
    }

    private fun numberColor(cell: DayCell, isSelected: Boolean): Int = when {
        cell.isToday && settings.todayShape != TodayShape.UNDERLINE -> scheme.onPrimary
        cell.isToday -> scheme.primary
        cell.isRestDay -> scheme.error
        isSelected -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }

    /** Holiday tag first, then event titles (or dots), at most two lines plus "+N". */
    private fun chips(cell: DayCell): List<View> {
        val chips = mutableListOf<View>()
        val holiday = cell.holiday?.takeIf { settings.showHolidays }
        if (holiday != null) {
            val (background, text) = if (holiday.isYomTov) {
                scheme.errorContainer to scheme.onErrorContainer
            } else {
                scheme.tertiaryContainer to scheme.onTertiaryContainer
            }
            chips += chip(HolidayNames.short(holiday.name), background, text)
        }
        if (!settings.showEventTitles) {
            if (cell.events.isNotEmpty()) chips += dots(cell)
            return chips
        }
        val room = 2 - chips.size
        cell.events.take(room).forEach { chips += chip(it.title, it.color, WHITE) }
        val hidden = cell.events.size - room
        if (hidden > 0) chips += chip("+$hidden", 0, scheme.onSurfaceVariant)
        return chips
    }

    private fun chip(label: String, background: Int, textColor: Int) = TextView(context).apply {
        text = label
        textSp(8.5f, scale)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(textColor)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(Ui.dp(context, 3f), 0, Ui.dp(context, 3f), 0)
        if (background != 0) this.background = Ui.rounded(context, background, 4f)
    }

    private fun dots(cell: DayCell) = TextView(context).apply {
        gravity = Gravity.CENTER
        textSp(8f, scale)
        text = SpannableStringBuilder().apply {
            cell.events.take(3).forEach { event ->
                val start = length
                append("● ")
                setSpan(ForegroundColorSpan(event.color), start, length, 0)
            }
        }
    }

    private fun chipParams() = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = Ui.dp(context, 1.5f)
    }

    private companion object {
        val WHITE = ColorScheme.opaque("FFFFFF")
    }
}
