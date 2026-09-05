package com.myappstore.alarmclock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen alarm screen shown over the lock screen while the alarm rings. */
class AlarmRingActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null

    private val finishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContentView(R.layout.activity_alarm_ring)

        val alarm = AlarmStore.get(this, intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1))
        val labelView = findViewById<TextView>(R.id.ringLabel)
        val clockView = findViewById<TextView>(R.id.ringClock)
        val remainingView = findViewById<TextView>(R.id.ringRemaining)
        val snoozeButton = findViewById<Button>(R.id.ringSnooze)

        clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        labelView.text = alarm?.label?.ifBlank { getString(R.string.app_name) }
            ?: getString(R.string.app_name)

        if (alarm != null) {
            snoozeButton.text = getString(R.string.snooze_minutes, alarm.snoozeMinutes)
            startCountdown(alarm.ringSeconds, remainingView)
        } else {
            snoozeButton.isEnabled = false
        }

        snoozeButton.setOnClickListener { sendToService(AlarmService.ACTION_SNOOZE) }
        findViewById<Button>(R.id.ringDismiss).setOnClickListener {
            sendToService(AlarmService.ACTION_STOP)
        }

        ContextCompat.registerReceiver(
            this,
            finishedReceiver,
            IntentFilter(AlarmService.ACTION_ALARM_FINISHED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun startCountdown(seconds: Int, view: TextView) {
        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val left = (millisUntilFinished / 1000).toInt()
                view.text = getString(R.string.ring_remaining, left / 60, left % 60)
            }

            override fun onFinish() {
                finish()
            }
        }.also { it.start() }
    }

    private fun sendToService(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        runCatching { unregisterReceiver(finishedReceiver) }
    }

    /** Back must not silently kill the alarm - the user has to choose snooze or dismiss. */
    override fun onBackPressed() = Unit
}
