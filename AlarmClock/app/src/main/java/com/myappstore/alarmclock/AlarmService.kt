package com.myappstore.alarmclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service that actually rings.
 *
 * When the alarm has `overrideSystemSound` set, the service ignores the phone's
 * own sound settings: it plays on the alarm stream with USAGE_ALARM audio
 * attributes (which bypasses silent and vibrate mode), raises the alarm stream
 * to the alarm's own volume, lifts Do-Not-Disturb when the user has granted
 * notification-policy access, and vibrates regardless of the ringer mode. Every
 * value it changes is restored when the alarm stops.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    private var previousAlarmVolume: Int? = null
    private var previousInterruptionFilter: Int? = null
    private var currentAlarm: Alarm? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }

        val alarmId = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1) ?: -1
        val alarm = AlarmStore.get(this, alarmId)
        if (alarm == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        currentAlarm = alarm

        startForeground(NOTIFICATION_ID, buildNotification(alarm))
        acquireWakeLock(alarm.ringSeconds)
        startRinging(alarm)

        startActivity(
            Intent(this, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            }
        )

        stopRunnable = Runnable { stopEverything() }.also {
            handler.postDelayed(it, alarm.ringSeconds * 1000L)
        }
        return START_STICKY
    }

    private fun startRinging(alarm: Alarm) {
        val am = getSystemService(AudioManager::class.java)
        audioManager = am

        if (alarm.overrideSystemSound) {
            liftDoNotDisturb()
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            previousAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = (max * alarm.volumePercent / 100).coerceIn(1, max)
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .build()
            .also { runCatching { am.requestAudioFocus(it) } }

        val uri: Uri = alarm.soundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setVolume(1f, 1f)
                prepare()
                start()
            }
        }

        if (alarm.vibrate) startVibration()
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        vibrator = v
        val pattern = longArrayOf(0, 700, 500)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0), attributes)
        }
    }

    /** Temporarily disables Do-Not-Disturb, if the user granted policy access. */
    private fun liftDoNotDisturb() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        val current = nm.currentInterruptionFilter
        if (current != NotificationManager.INTERRUPTION_FILTER_ALL) {
            previousInterruptionFilter = current
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
    }

    private fun acquireWakeLock(seconds: Int) {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmClock:ring").apply {
            setReferenceCounted(false)
            acquire(seconds * 1000L + 10_000L)
        }
    }

    private fun stopEverything() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null

        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null

        runCatching { vibrator?.cancel() }
        vibrator = null

        focusRequest?.let { req -> runCatching { audioManager?.abandonAudioFocusRequest(req) } }
        focusRequest = null

        previousAlarmVolume?.let { volume ->
            runCatching { audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0) }
        }
        previousAlarmVolume = null

        previousInterruptionFilter?.let { filter ->
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) {
                runCatching { nm.setInterruptionFilter(filter) }
            }
        }
        previousInterruptionFilter = null

        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null

        sendBroadcast(Intent(ACTION_ALARM_FINISHED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
    }

    private fun buildNotification(alarm: Alarm): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_alarm),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )

        val fullScreen = PendingIntent.getActivity(
            this,
            alarm.id,
            Intent(this, AlarmRingActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getService(
            this,
            alarm.id + 100_000,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = alarm.label.ifBlank { getString(R.string.app_name) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(String.format("%02d:%02d", alarm.hour, alarm.minute))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, getString(R.string.dismiss), dismiss)
            .build()
    }

    companion object {
        const val ACTION_START = "com.myappstore.alarmclock.START"
        const val ACTION_STOP = "com.myappstore.alarmclock.STOP"
        const val ACTION_ALARM_FINISHED = "com.myappstore.alarmclock.FINISHED"
        private const val CHANNEL_ID = "alarm_ring"
        private const val NOTIFICATION_ID = 42
    }
}
