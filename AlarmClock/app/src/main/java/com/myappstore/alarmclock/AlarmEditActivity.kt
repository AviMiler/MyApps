package com.myappstore.alarmclock

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar

/** Create or edit a single alarm. */
class AlarmEditActivity : AppCompatActivity() {

    private var alarm: Alarm? = null

    private lateinit var timePicker: TimePicker
    private lateinit var labelInput: EditText
    private lateinit var minutesInput: EditText
    private lateinit var secondsInput: EditText
    private lateinit var snoozeInput: EditText
    private lateinit var daysGroup: MaterialButtonToggleGroup
    private lateinit var overrideSwitch: MaterialSwitch
    private lateinit var vibrateSwitch: MaterialSwitch
    private lateinit var volumeBar: SeekBar
    private lateinit var volumeLabel: TextView
    private lateinit var durationSummary: TextView

    private val dayButtonIds by lazy {
        listOf(
            R.id.daySun, R.id.dayMon, R.id.dayTue, R.id.dayWed,
            R.id.dayThu, R.id.dayFri, R.id.daySat
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_edit)

        timePicker = findViewById(R.id.timePicker)
        labelInput = findViewById(R.id.labelInput)
        minutesInput = findViewById(R.id.ringMinutes)
        secondsInput = findViewById(R.id.ringSeconds)
        snoozeInput = findViewById(R.id.snoozeInput)
        daysGroup = findViewById(R.id.daysGroup)
        overrideSwitch = findViewById(R.id.overrideSwitch)
        vibrateSwitch = findViewById(R.id.vibrateSwitch)
        volumeBar = findViewById(R.id.volumeBar)
        volumeLabel = findViewById(R.id.volumeLabel)
        durationSummary = findViewById(R.id.durationSummary)

        timePicker.setIs24HourView(true)

        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val existing = if (id >= 0) AlarmStore.get(this, id) else null
        alarm = existing
        bind(existing)

        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                volumeLabel.text = getString(R.string.volume_value, progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updateDurationSummary()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        }
        minutesInput.addTextChangedListener(watcher)
        secondsInput.addTextChangedListener(watcher)

        overrideSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) maybeAskForDndAccess()
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }
        findViewById<Button>(R.id.deleteButton).apply {
            visibility = if (existing == null) android.view.View.GONE else android.view.View.VISIBLE
            setOnClickListener { deleteAlarm() }
        }
    }

    private fun bind(a: Alarm?) {
        val now = Calendar.getInstance()
        timePicker.hour = a?.hour ?: now.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = a?.minute ?: now.get(Calendar.MINUTE)
        labelInput.setText(a?.label ?: "")

        val total = a?.ringSeconds ?: 60
        minutesInput.setText((total / 60).toString())
        secondsInput.setText((total % 60).toString())
        snoozeInput.setText((a?.snoozeMinutes ?: 5).toString())

        overrideSwitch.isChecked = a?.overrideSystemSound ?: true
        vibrateSwitch.isChecked = a?.vibrate ?: true
        volumeBar.progress = a?.volumePercent ?: 100
        volumeLabel.text = getString(R.string.volume_value, volumeBar.progress)

        val days = a?.days ?: emptySet<Int>()
        dayButtonIds.forEachIndexed { index, buttonId ->
            // Calendar.SUNDAY == 1, so index 0 maps to Sunday.
            if (days.contains(index + 1)) daysGroup.check(buttonId)
        }
        updateDurationSummary()
    }

    private fun readDurationSeconds(): Int {
        val minutes = minutesInput.text.toString().trim().toIntOrNull() ?: 0
        val seconds = secondsInput.text.toString().trim().toIntOrNull() ?: 0
        return minutes * 60 + seconds
    }

    private fun updateDurationSummary() {
        val total = readDurationSeconds()
        durationSummary.text = getString(R.string.duration_summary, total, total / 60, total % 60)
    }

    private fun save() {
        val total = readDurationSeconds()
        if (total <= 0) {
            Toast.makeText(this, R.string.error_duration, Toast.LENGTH_LONG).show()
            return
        }

        val days = mutableSetOf<Int>()
        dayButtonIds.forEachIndexed { index, buttonId ->
            if (daysGroup.checkedButtonIds.contains(buttonId)) days.add(index + 1)
        }

        val updated = (alarm ?: Alarm(id = AlarmStore.newId(this), hour = 0, minute = 0)).apply {
            hour = timePicker.hour
            minute = timePicker.minute
            label = labelInput.text.toString().trim()
            this.days = days
            ringSeconds = total
            snoozeMinutes = snoozeInput.text.toString().trim().toIntOrNull()?.coerceAtLeast(1) ?: 5
            overrideSystemSound = overrideSwitch.isChecked
            vibrate = vibrateSwitch.isChecked
            volumePercent = volumeBar.progress.coerceIn(1, 100)
            enabled = true
        }

        AlarmStore.save(this, updated)
        AlarmScheduler.schedule(this, updated)
        Toast.makeText(this, R.string.alarm_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun deleteAlarm() {
        alarm?.let {
            AlarmScheduler.cancel(this, it.id)
            AlarmStore.delete(this, it.id)
        }
        finish()
    }

    /** Do-Not-Disturb can only be lifted when the user grants notification-policy access. */
    private fun maybeAskForDndAccess() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) return
        Toast.makeText(this, R.string.dnd_access_hint, Toast.LENGTH_LONG).show()
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }
}
