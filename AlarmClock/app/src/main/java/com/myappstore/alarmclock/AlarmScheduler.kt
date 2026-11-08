package com.myappstore.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules and cancels exact alarms with [AlarmManager]. */
object AlarmScheduler {

    const val EXTRA_ALARM_ID = "alarm_id"

    fun rescheduleAll(context: Context) {
        AlarmStore.all(context).forEach { schedule(context, it) }
    }

    fun schedule(context: Context, alarm: Alarm) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, alarm.id)
        manager.cancel(pending)
        if (!alarm.enabled) return

        val triggerAt = alarm.nextTriggerMillis()
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact(context)) {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pending)
        } else {
            // Without the exact-alarm permission the best we can do is an inexact wakeup.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, alarmId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, alarmId))
    }

    fun canScheduleExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    private fun pendingIntent(context: Context, alarmId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.myappstore.alarmclock.FIRE"
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
