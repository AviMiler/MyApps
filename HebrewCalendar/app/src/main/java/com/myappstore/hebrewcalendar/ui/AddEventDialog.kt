package com.myappstore.hebrewcalendar.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.calendar.CalendarRepository
import com.myappstore.hebrewcalendar.hebrew.DateLabels
import com.myappstore.hebrewcalendar.hebrew.HebrewAnniversary
import com.myappstore.hebrewcalendar.hebrew.HebrewDate
import com.myappstore.hebrewcalendar.model.WritableCalendar
import com.myappstore.hebrewcalendar.theme.ColorScheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor

/**
 * Adds an event to a (Google) calendar on the selected day, optionally repeating
 * every year on the same Hebrew date for as many years as the user chooses.
 */
class AddEventDialog(
    private val activity: Activity,
    private val repository: CalendarRepository,
    private val executor: Executor,
    private val scheme: ColorScheme,
    private val onSaved: () -> Unit
) {
    private val theme = SystemBars.dialogTheme(scheme)

    fun show(date: LocalDate, calendars: List<WritableCalendar>) {
        if (calendars.isEmpty()) {
            Toast.makeText(activity, R.string.no_writable_calendars, Toast.LENGTH_LONG).show()
            return
        }
        val themed = ContextThemeWrapper(activity, theme)
        val view = LayoutInflater.from(themed).inflate(R.layout.dialog_add_event, null)
        val titleInput = view.findViewById<EditText>(R.id.input_title)
        val calendarSpinner = view.findViewById<Spinner>(R.id.spinner_calendar)
        val allDay = view.findViewById<CheckBox>(R.id.checkbox_all_day)
        val timeButton = view.findViewById<Button>(R.id.button_time)
        val repeat = view.findViewById<CheckBox>(R.id.checkbox_repeat)
        val yearsRow = view.findViewById<View>(R.id.repeat_years_row)
        val yearsInput = view.findViewById<EditText>(R.id.input_years)

        calendarSpinner.adapter = ArrayAdapter(themed, android.R.layout.simple_spinner_dropdown_item, calendars)
        var time = LocalTime.of(9, 0)
        timeButton.text = activity.getString(R.string.start_time, time.format(TIME_FORMAT))
        allDay.setOnCheckedChangeListener { _, isChecked -> timeButton.visibility = if (isChecked) View.GONE else View.VISIBLE }
        repeat.setOnCheckedChangeListener { _, isChecked -> yearsRow.visibility = if (isChecked) View.VISIBLE else View.GONE }
        timeButton.setOnClickListener {
            TimePickerDialog(activity, theme, { _, hour, minute ->
                time = LocalTime.of(hour, minute)
                timeButton.text = activity.getString(R.string.start_time, time.format(TIME_FORMAT))
            }, time.hour, time.minute, true).show()
        }

        val hebrewDate = HebrewDate.of(date)
        val dialog = AlertDialog.Builder(activity, theme)
            .setTitle(DateLabels.fullHebrewDate(hebrewDate))
            .setMessage(DateLabels.fullGregorianDate(date))
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        // Set the click listener after show() so invalid input keeps the dialog open.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    titleInput.error = activity.getString(R.string.title_required)
                    return@setOnClickListener
                }
                val years = yearsInput.text.toString().toIntOrNull()
                if (repeat.isChecked && (years == null || years !in 1..MAX_YEARS)) {
                    yearsInput.error = activity.getString(R.string.years_invalid, MAX_YEARS)
                    return@setOnClickListener
                }
                val dates = if (repeat.isChecked) HebrewAnniversary.dates(hebrewDate, years ?: 1) else listOf(date)
                val description = activity.getString(R.string.event_description, DateLabels.hebrewDayAndMonth(hebrewDate))
                val calendar = calendars[calendarSpinner.selectedItemPosition]
                dialog.dismiss()
                save(calendar, title, description, dates, if (allDay.isChecked) null else time)
            }
        }
        dialog.show()
    }

    private fun save(calendar: WritableCalendar, title: String, description: String, dates: List<LocalDate>, time: LocalTime?) {
        executor.execute {
            val written = repository.insertEvents(calendar.id, title, description, dates, time)
            activity.runOnUiThread {
                if (written > 0) {
                    Toast.makeText(activity, activity.getString(R.string.events_added, written), Toast.LENGTH_SHORT).show()
                    onSaved()
                } else {
                    Toast.makeText(activity, R.string.events_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private companion object {
        /** Upper bound for Hebrew-date yearly repeats (one event is written per year). */
        const val MAX_YEARS = 120
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
