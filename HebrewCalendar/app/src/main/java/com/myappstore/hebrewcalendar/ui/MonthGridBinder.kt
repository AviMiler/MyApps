package com.myappstore.hebrewcalendar.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.hebrew.DateLabels
import com.myappstore.hebrewcalendar.model.DayCell
import java.time.LocalDate

/** Draws the month grid of the app screen into plain LinearLayout rows. */
class MonthGridBinder(
    header: LinearLayout,
    private val grid: LinearLayout,
    private val onDayClick: (LocalDate) -> Unit
) {
    private val inflater = LayoutInflater.from(grid.context)

    init {
        DateLabels.WEEKDAY_LETTERS.forEachIndexed { column, letter ->
            val label = TextView(header.context).apply {
                text = letter
                gravity = Gravity.CENTER
                textSize = 12f
                val color = if (column == 6) R.color.rest_day else R.color.text_secondary
                setTextColor(ContextCompat.getColor(context, color))
            }
            header.addView(label, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Replaces the grid with [weeks]; [selected] gets an outline. */
    fun bind(weeks: List<List<DayCell?>>, selected: LocalDate) {
        grid.removeAllViews()
        for (week in weeks) {
            val row = LinearLayout(grid.context).apply { orientation = LinearLayout.HORIZONTAL }
            week.forEach { cell -> row.addView(cellView(row, cell, selected)) }
            grid.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun cellView(row: LinearLayout, cell: DayCell?, selected: LocalDate): View {
        val view = inflater.inflate(R.layout.item_day_cell, row, false)
        if (cell == null) return view
        val context = view.context
        view.findViewById<TextView>(R.id.cell_primary).apply {
            text = cell.primaryLabel
            setTextColor(ContextCompat.getColor(context, CellStyle.primaryColor(cell)))
        }
        view.findViewById<TextView>(R.id.cell_secondary).text = cell.secondaryLabel
        view.findViewById<TextView>(R.id.cell_marker).text = CellStyle.markerText(cell)
        view.setBackgroundResource(CellStyle.background(cell, cell.date == selected, R.drawable.cell_default))
        view.contentDescription = DateLabels.fullGregorianDate(cell.date)
        view.setOnClickListener { onDayClick(cell.date) }
        return view
    }
}
