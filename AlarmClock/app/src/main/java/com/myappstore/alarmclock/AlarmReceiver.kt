package com.myappstore.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the AlarmManager wakeup (and boot completion) and starts the ringing service. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            AlarmScheduler.rescheduleAll(context)
            return
        }

        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val alarm = AlarmStore.get(context, alarmId) ?: return

        context.startForegroundService(
            Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_START
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            }
        )

        // A repeating alarm is re-armed for its next day; a one shot switches itself off.
        if (alarm.days.isEmpty()) {
            alarm.enabled = false
            AlarmStore.save(context, alarm)
        } else {
            AlarmScheduler.schedule(context, alarm)
        }
    }
}
