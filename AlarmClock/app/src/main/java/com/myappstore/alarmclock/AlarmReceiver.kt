package com.myappstore.alarmclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/** Receives the AlarmManager wakeup and starts the ringing service. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Keep the CPU awake until the service has taken over (released by its timeout).
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmClock:receiver")
            .apply { setReferenceCounted(false); acquire(15_000L) }

        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val scheduledFor = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, 0L)
        val now = System.currentTimeMillis()
        val lateness = if (scheduledFor > 0) "${now - scheduledFor} ms after schedule" else "schedule time unknown"
        val alarm = AlarmStore.get(context, alarmId)
        AppLog.i(
            context, "Receiver",
            "FIRE alarm=$alarmId found=${alarm != null} fired at ${AppLog.formatTime(now)} ($lateness)"
        )
        if (alarm == null) return

        // Re-arm BEFORE doing anything that can fail, so one bad ring never kills the whole series.
        // A repeating alarm is armed for its next day; a one shot switches itself off.
        if (alarm.days.isEmpty()) {
            alarm.enabled = false
            AlarmStore.save(context, alarm)
            AppLog.i(context, "Receiver", "alarm $alarmId was one-shot -> disabled")
        } else {
            AlarmScheduler.schedule(context, alarm, "re-arm after fire")
        }

        try {
            context.startForegroundService(
                Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_START
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                }
            )
        } catch (t: Throwable) {
            AppLog.e(context, "Receiver", "startForegroundService FAILED for alarm $alarmId - posting fallback", t)
            postFallbackNotification(context, alarm)
        }
    }

    /** Last resort when the ringing service cannot start: a loud alarm-channel notification. */
    private fun postFallbackNotification(context: Context, alarm: Alarm) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nm.createNotificationChannel(
                NotificationChannel(
                    FALLBACK_CHANNEL,
                    context.getString(R.string.channel_fallback),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attributes)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 700, 500, 700, 500, 700)
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
            val open = PendingIntent.getActivity(
                context,
                alarm.id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, FALLBACK_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(alarm.label.ifBlank { context.getString(R.string.app_name) })
                .setContentText("%02d:%02d".format(alarm.hour, alarm.minute))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setAutoCancel(true)
                .setTimeoutAfter(alarm.ringSeconds * 1000L)
                .build()
            nm.notify(FALLBACK_NOTIFICATION_BASE + alarm.id, notification)
            AppLog.i(context, "Receiver", "fallback notification posted for alarm ${alarm.id}")
        }.onFailure { AppLog.e(context, "Receiver", "fallback notification failed", it) }
    }

    private companion object {
        const val FALLBACK_CHANNEL = "alarm_fallback"
        const val FALLBACK_NOTIFICATION_BASE = 1000
    }
}
