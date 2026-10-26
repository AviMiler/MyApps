package com.myappstore.hebrewcalendar.ui

import android.app.TimePickerDialog
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.calendar.CalendarRepository
import com.myappstore.hebrewcalendar.hebrew.DateLabels
import com.myappstore.hebrewcalendar.hebrew.HebrewAnniversary
import com.myappstore.hebrewcalendar.hebrew.HebrewDate
import com.myappstore.hebrewcalendar.model.WritableCalendar
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor

/**
 * Adds an event to a (Google) calendar on the selected day, optionally repeating
 * every year on the same Hebrew date.
 */
class AddEventDialog(
    private val activity: AppCompatActivity,
    private val repository: CalendarRepository,
    private val executor: Executor,
    private val onSaved: () -> Unit
) {

    fun show(date: LocalDate, calendars: List<WritableCalendar>) {
        if (calendars.isEmpty()) {
            Toast.makeText(activity, R.string.no_writable_calendars, Toast.LENGTH_LONG).show()
            return
        }
        val view = activity.layoutInflater.inflate(R.layout.dialog_add_event, null)
        val titleInput = view.findViewById<EditText>(R.id.input_title)
        val calendarSpinner = view.findViewById<Spinner>(R.id.spinner_calendar)
        val allDay = view.findViewById<CheckBox>(R.id.checkbox_all_day)
        val timeButton = view.findViewById<Button>(R.id.button_time)
        val repeat = view.findViewById<CheckBox>(R.id.checkbox_repeat)

        calendarSpinner.adapter =
            ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, calendars)
        repeat.text = activity.getString(R.string.repeat_hebrew_yearly, REPEAT_YEARS)
        var time = LocalTime.of(9, 0)
        timeButton.text = activity.getString(R.string.start_time, time.format(TIME_FORMAT))
        allDay.setOnCheckedChangeListener { _, isChecked ->
            timeButton.visibility = if (isChecked) View.GONE else View.VISIBLE
        }
        timeButton.setOnClickListener {
            TimePickerDialog(activity, { _, hour, minute ->
                time = LocalTime.of(hour, minute)
                timeButton.text = activity.getString(R.string.start_time, time.format(TIME_FORMAT))
            }, time.hour, time.minute, true).show()
        }

        val hebrewDate = HebrewDate.of(date)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(DateLabels.fullHebrewDate(hebrewDate))
            .setMessage(DateLabels.fullGregorianDate(date))
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        // Set the click listener after show() so an empty title keeps the dialog open.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    titleInput.error = activity.getString(R.string.title_required)
                    return@setOnClickListener
                }
                val dates = if (repeat.isChecked) HebrewAnniversary.dates(hebrewDate, REPEAT_YEARS) else listOf(date)
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
        const val REPEAT_YEARS = 10
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
