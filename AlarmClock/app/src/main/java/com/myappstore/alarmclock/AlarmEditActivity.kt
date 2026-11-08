package com.myappstore.alarmclock

import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar

/** Create or edit a single alarm. */
class AlarmEditActivity : AppCompatActivity() {

    private var alarm: Alarm? = null
    private var selectedSoundUri: Uri? = null

    private lateinit var timePicker: TimePicker
    private lateinit var labelInput: EditText
    private lateinit var minutesInput: EditText
    private lateinit var secondsInput: EditText
    private lateinit var daysGroup: MaterialButtonToggleGroup
    private lateinit var overrideSwitch: MaterialSwitch
    private lateinit var vibrateSwitch: MaterialSwitch
    private lateinit var volumeBar: SeekBar
    private lateinit var volumeLabel: TextView
    private lateinit var durationSummary: TextView
    private lateinit var soundButton: Button

    private val dayButtonIds by lazy {
        listOf(
            R.id.daySun, R.id.dayMon, R.id.dayTue, R.id.dayWed,
            R.id.dayThu, R.id.dayFri, R.id.daySat
        )
    }

    private val pickRingtone = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        selectedSoundUri = uri
        updateSoundButtonLabel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_edit)

        timePicker = findViewById(R.id.timePicker)
        labelInput = findViewById(R.id.labelInput)
        minutesInput = findViewById(R.id.ringMinutes)
        secondsInput = findViewById(R.id.ringSeconds)
        daysGroup = findViewById(R.id.daysGroup)
        overrideSwitch = findViewById(R.id.overrideSwitch)
        vibrateSwitch = findViewById(R.id.vibrateSwitch)
        volumeBar = findViewById(R.id.volumeBar)
        volumeLabel = findViewById(R.id.volumeLabel)
        durationSummary = findViewById(R.id.durationSummary)
        soundButton = findViewById(R.id.soundButton)

        timePicker.setIs24HourView(true)

        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val existing = if (id >= 0) AlarmStore.get(this, id) else null
        alarm = existing
        selectedSoundUri = existing?.soundUri?.let { Uri.parse(it) }
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

        soundButton.setOnClickListener { openRingtonePicker() }

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
        updateSoundButtonLabel()
    }

    /** Opens the system's own alarm-sound picker, listing every alarm tone installed on the phone. */
    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.sound_picker_title))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedSoundUri)
        }
        pickRingtone.launch(intent)
    }

    private fun updateSoundButtonLabel() {
        val uri = selectedSoundUri
        soundButton.text = if (uri == null) {
            getString(R.string.sound_default)
        } else {
            runCatching { RingtoneManager.getRingtone(this, uri)?.getTitle(this) }
                .getOrNull() ?: getString(R.string.sound_default)
        }
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
            overrideSystemSound = overrideSwitch.isChecked
            vibrate = vibrateSwitch.isChecked
            volumePercent = volumeBar.progress.coerceIn(1, 100)
            soundUri = selectedSoundUri?.toString()
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
