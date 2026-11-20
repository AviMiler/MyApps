package com.myappstore.alarmclock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen alarm screen shown over the lock screen while the alarm rings.
 * It follows the service's single ring session (title and end time), so several
 * alarms at the same moment show up as one screen.
 */
class AlarmRingActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var labelView: TextView
    private lateinit var remainingView: TextView
    private lateinit var defaultLabel: String

    private val finishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = finish()
    }

    private val tick = object : Runnable {
        override fun run() {
            val leftMs = AlarmService.ringEndsAt - SystemClock.elapsedRealtime()
            if (leftMs <= 0) {
                finish()
                return
            }
            val left = ((leftMs + 999) / 1000).toInt()
            remainingView.text = getString(R.string.ring_remaining, left / 60, left % 60)
            labelView.text = AlarmService.activeTitle.ifBlank { defaultLabel }
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContentView(R.layout.activity_alarm_ring)

        val alarm = AlarmStore.get(this, intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1))
        defaultLabel = alarm?.label?.ifBlank { getString(R.string.app_name) }
            ?: getString(R.string.app_name)
        labelView = findViewById(R.id.ringLabel)
        remainingView = findViewById(R.id.ringRemaining)
        labelView.text = defaultLabel
        findViewById<TextView>(R.id.ringClock).text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        findViewById<Button>(R.id.ringDismiss).setOnClickListener {
            AppLog.i(this, "RingScreen", "dismiss pressed")
            sendToService(AlarmService.ACTION_STOP)
        }

        ContextCompat.registerReceiver(
            this,
            finishedReceiver,
            IntentFilter(AlarmService.ACTION_ALARM_FINISHED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        AppLog.i(this, "RingScreen", "shown")
        handler.post(tick)
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
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(finishedReceiver) }
    }

    /** Back must not silently kill the alarm - the user has to press dismiss. */
    @Deprecated("Back is intentionally disabled while ringing")
    override fun onBackPressed() = Unit
}
