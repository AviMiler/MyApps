package com.myappstore.alarmclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service that actually rings.
 *
 * Everything that rings at the same time shares ONE ring session: a single player,
 * a single notification, a single ring screen and a single stop timer that runs until
 * the longest of the alarms is over. A second alarm joining a session never creates a
 * second player and never shortens (or double-restores) anything the first one set up.
 *
 * When an alarm has `overrideSystemSound` set, the session plays on the alarm stream
 * with USAGE_ALARM (bypasses silent/vibrate), raises the alarm stream to the alarm's
 * volume, lifts Do-Not-Disturb when policy access was granted, and vibrates
 * regardless of the ringer mode. Every value it changes is restored exactly once,
 * to what it was before the first alarm of the session.
 *
 * The chosen sound is tried first; if it cannot be played the default alarm,
 * ringtone and notification sounds are tried in turn, so a deleted or unreadable
 * ringtone can never leave the alarm silent.
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
    private var overrideVolumePercent = 0

    private val activeAlarms = LinkedHashMap<Int, Alarm>()
    private var soundStarted = false
    private var ringActivityLaunched = false
    private var soundCandidates: List<Uri> = emptyList()
    private var candidateIndex = 0

    /** True while no ring session is running (also the state of a fresh service instance). */
    private var stopped = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppLog.i(this, TAG, "dismiss requested")
            stopEverything("dismissed by user")
            return START_NOT_STICKY
        }

        val alarmId = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1) ?: -1
        val alarm = AlarmStore.get(this, alarmId)

        if (alarm == null) {
            AppLog.e(this, TAG, "start requested for unknown alarm $alarmId (intent=${intent?.action})")
            // Started with startForegroundService, so startForeground is mandatory or the app crashes.
            startForeground(NOTIFICATION_ID, buildNotification(activeAlarms.values.toList()))
            if (stopped) stopEverythingNow("unknown alarm")
            return START_NOT_STICKY
        }

        val joining = !stopped
        if (!joining) {
            stopped = false
            activeAlarms.clear()
            soundStarted = false
            ringActivityLaunched = false
            overrideVolumePercent = 0
            ringEndsAt = 0L
        }
        activeAlarms[alarm.id] = alarm

        ringEndsAt = maxOf(ringEndsAt, SystemClock.elapsedRealtime() + alarm.ringSeconds * 1000L)
        val notification = buildNotification(activeAlarms.values.toList())
        activeTitle = notificationTitle(activeAlarms.values.toList())
        startForeground(NOTIFICATION_ID, notification)

        AppLog.i(
            this, TAG,
            (if (joining) "JOIN running ring session" else "START ring session") +
                ": alarm=${alarm.id} ${"%02d:%02d".format(alarm.hour, alarm.minute)} " +
                "label='${alarm.label}' ring=${alarm.ringSeconds}s override=${alarm.overrideSystemSound} " +
                "vibrate=${alarm.vibrate} active=${activeAlarms.keys}"
        )
        if (!joining) AppLog.i(this, TAG, "state before ringing:\n" + AppLog.snapshot(this).trimEnd())

        acquireWakeLock()
        runCatching { applyAudioOverride(alarm) }
            .onFailure { AppLog.e(this, TAG, "audio override failed", it) }
        if (!soundStarted) {
            soundStarted = true
            runCatching { startSound(alarm) }
                .onFailure { AppLog.e(this, TAG, "startSound crashed", it) }
        }
        if (alarm.vibrate) startVibration()
        launchRingScreen(alarm)
        rescheduleStopTimer()
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ audio

    private fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Raises the alarm stream / lifts DnD. The original values are remembered once per session. */
    private fun applyAudioOverride(alarm: Alarm) {
        val am = audioManager ?: getSystemService(AudioManager::class.java).also { audioManager = it }
        if (!alarm.overrideSystemSound) return

        liftDoNotDisturb()
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (previousAlarmVolume == null) previousAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        overrideVolumePercent = maxOf(overrideVolumePercent, alarm.volumePercent)
        val target = (max * overrideVolumePercent / 100).coerceIn(1, max)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
            .onFailure { AppLog.e(this, TAG, "setStreamVolume failed", it) }
        AppLog.i(
            this, TAG,
            "alarm volume ${previousAlarmVolume} -> $target (max $max), restores to ${previousAlarmVolume} at end"
        )
    }

    private fun startSound(alarm: Alarm) {
        val am = audioManager ?: getSystemService(AudioManager::class.java).also { audioManager = it }
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(alarmAttributes())
            .build()
            .also { req -> runCatching { am.requestAudioFocus(req) } }

        soundCandidates = listOfNotNull(
            alarm.soundUri?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).distinct()
        candidateIndex = 0
        playNextCandidate()
    }

    /** Plays the next sound that works; silently skipping a broken one is exactly what used to happen. */
    private fun playNextCandidate() {
        while (candidateIndex < soundCandidates.size) {
            val uri = soundCandidates[candidateIndex++]
            val p = MediaPlayer()
            try {
                p.setAudioAttributes(alarmAttributes())
                p.setDataSource(this, uri)
                p.isLooping = true
                p.setVolume(1f, 1f)
                p.setOnErrorListener { mp, what, extra ->
                    AppLog.e(this, TAG, "MediaPlayer error what=$what extra=$extra while playing $uri")
                    runCatching { mp.release() }
                    if (player === mp) {
                        player = null
                        playNextCandidate()
                    }
                    true
                }
                p.prepare()
                p.start()
                player = p
                AppLog.i(
                    this, TAG,
                    "sound playing: $uri (candidate $candidateIndex of ${soundCandidates.size}, isPlaying=${p.isPlaying})"
                )
                return
            } catch (e: Exception) {
                AppLog.e(this, TAG, "sound FAILED, trying next: $uri", e)
                runCatching { p.release() }
            }
        }
        player = null
        AppLog.e(this, TAG, "NO sound could be played - vibration only")
    }

    private fun startVibration() {
        if (vibrator != null) return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        vibrator = v
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), alarmAttributes())
        }.onFailure { AppLog.e(this, TAG, "vibration failed", it) }
    }

    /** Temporarily disables Do-Not-Disturb, if the user granted policy access. */
    private fun liftDoNotDisturb() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            AppLog.i(this, TAG, "DnD policy access NOT granted - cannot lift Do-Not-Disturb")
            return
        }
        val current = nm.currentInterruptionFilter
        if (current != NotificationManager.INTERRUPTION_FILTER_ALL && previousInterruptionFilter == null) {
            previousInterruptionFilter = current
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
            AppLog.i(this, TAG, "DnD lifted (was filter $current)")
        }
    }

    // ------------------------------------------------------------------ session lifetime

    private fun acquireWakeLock() {
        val ms = (ringEndsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 10_000L
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmClock:ring")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(ms)
    }

    private fun launchRingScreen(alarm: Alarm) {
        if (ringActivityLaunched) return
        ringActivityLaunched = true
        runCatching {
            startActivity(
                Intent(this, AlarmRingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                }
            )
            AppLog.i(this, TAG, "ring screen launched")
        }.onFailure {
            // Normal when the app is in the background: the full-screen notification takes over.
            AppLog.e(this, TAG, "ring screen could not be launched directly", it)
        }
    }

    /** One stop timer for the whole session, always aimed at the longest alarm. */
    private fun rescheduleStopTimer() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        val delay = (ringEndsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        stopRunnable = Runnable { stopEverything("ring time elapsed") }.also {
            handler.postDelayed(it, delay)
        }
        AppLog.i(this, TAG, "will stop in ${delay / 1000}s (at ${AppLog.formatTime(System.currentTimeMillis() + delay)})")
    }

    /** Public entry; ignores calls when no session is running. */
    private fun stopEverything(reason: String) {
        if (stopped) return
        stopEverythingNow(reason)
    }

    private fun stopEverythingNow(reason: String) {
        stopped = true
        AppLog.i(this, TAG, "STOP ring session: $reason (alarms ${activeAlarms.keys})")

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
            AppLog.i(this, TAG, "alarm volume restored to $volume")
        }
        previousAlarmVolume = null

        previousInterruptionFilter?.let { filter ->
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) {
                runCatching { nm.setInterruptionFilter(filter) }
                AppLog.i(this, TAG, "DnD restored to filter $filter")
            }
        }
        previousInterruptionFilter = null

        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null

        activeAlarms.clear()
        soundStarted = false
        overrideVolumePercent = 0
        ringEndsAt = 0L
        activeTitle = ""

        sendBroadcast(Intent(ACTION_ALARM_FINISHED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything("service destroyed")
    }

    // ------------------------------------------------------------------ notification

    private fun notificationTitle(alarms: List<Alarm>): String =
        alarms.map { it.label.ifBlank { "%02d:%02d".format(it.hour, it.minute) } }
            .distinct()
            .joinToString(" · ")
            .ifBlank { getString(R.string.app_name) }

    private fun buildNotification(alarms: List<Alarm>): Notification {
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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        val first = alarms.firstOrNull()
        val requestCode = first?.id ?: 0
        val fullScreen = PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, AlarmRingActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, requestCode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getService(
            this,
            100_000,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notificationTitle(alarms))
            .setContentText(alarms.joinToString(", ") { "%02d:%02d".format(it.hour, it.minute) })
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
        private const val TAG = "Service"

        /** [SystemClock.elapsedRealtime] at which the current ring session ends; 0 when not ringing. */
        @Volatile
        var ringEndsAt: Long = 0L

        /** Title shown on the ring screen: the labels of every alarm in the session. */
        @Volatile
        var activeTitle: String = ""
    }
}
