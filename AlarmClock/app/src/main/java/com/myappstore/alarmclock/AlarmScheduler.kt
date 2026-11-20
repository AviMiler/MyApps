package com.myappstore.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules and cancels exact alarms with [AlarmManager]. */
object AlarmScheduler {

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_TRIGGER_AT = "trigger_at"

    fun rescheduleAll(context: Context, reason: String) {
        val alarms = AlarmStore.all(context)
        AppLog.i(context, "Scheduler", "rescheduleAll ($reason): ${alarms.size} alarms, exact=${canScheduleExact(context)}")
        alarms.forEach { schedule(context, it, reason) }
    }

    fun schedule(context: Context, alarm: Alarm, reason: String = "") {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pendingIntent(context, alarm.id, 0L))
        if (!alarm.enabled) {
            AppLog.i(context, "Scheduler", "alarm ${alarm.id} is disabled - cancelled ($reason)")
            return
        }

        val triggerAt = alarm.nextTriggerMillis()
        val pending = pendingIntent(context, alarm.id, triggerAt)
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var mode = "exact (setAlarmClock)"
        try {
            if (canScheduleExact(context)) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pending)
            } else {
                mode = "INEXACT (no exact-alarm permission)"
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the call.
            mode = "INEXACT (SecurityException)"
            AppLog.e(context, "Scheduler", "setAlarmClock denied for alarm ${alarm.id}", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        AppLog.i(
            context, "Scheduler",
            "alarm ${alarm.id} ${"%02d:%02d".format(alarm.hour, alarm.minute)} -> ${AppLog.formatTime(triggerAt)} " +
                "[$mode] days=${alarm.days.sorted()} ($reason)"
        )
    }

    fun cancel(context: Context, alarmId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, alarmId, 0L))
        AppLog.i(context, "Scheduler", "alarm $alarmId cancelled")
    }

    fun canScheduleExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    private fun pendingIntent(context: Context, alarmId: Int, triggerAt: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.myappstore.alarmclock.FIRE"
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_TRIGGER_AT, triggerAt)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
