package com.myappstore.smsforwarder.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.sms.runAsync

/**
 * One alarm, always set to the earliest waiting message (undo window, retry or the
 * end of a hold). Exact alarms are used when the user allowed them, so messages
 * held for Shabbat or quiet hours go out right on time even in deep sleep.
 */
class Scheduler(private val context: Context) {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canUseExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun scheduleAt(timeMillis: Long) {
        val at = maxOf(timeMillis, System.currentTimeMillis() + MIN_DELAY_MS)
        val operation = operation()
        try {
            if (canUseExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    fun cancel() = alarms.cancel(operation())

    private fun operation(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_DISPATCH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val MIN_DELAY_MS = 1_000L
    }
}

/** Fired by [Scheduler]: sends whatever is due and re-arms for the next one. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISPATCH) return
        runAsync("AlarmReceiver") { Graph.engine.dispatchDue() }
    }

    companion object {
        const val ACTION_DISPATCH = "com.myappstore.smsforwarder.action.DISPATCH"
    }
}

/** Alarms do not survive a reboot or an app update, so re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> runAsync("BootReceiver") { Graph.engine.dispatchDue() }
        }
    }
}

/** Buttons in notifications: cancel / send now / retry. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (id < 0) return
        when (intent.action) {
            ACTION_CANCEL -> runAsync(TAG) { Graph.engine.cancel(id) }
            ACTION_SEND_NOW -> runAsync(TAG) { Graph.engine.sendNow(id) }
            ACTION_RETRY -> runAsync(TAG) { Graph.engine.retry(id) }
        }
    }

    companion object {
        private const val TAG = "ActionReceiver"
        const val ACTION_CANCEL = "com.myappstore.smsforwarder.action.CANCEL"
        const val ACTION_SEND_NOW = "com.myappstore.smsforwarder.action.SEND_NOW"
        const val ACTION_RETRY = "com.myappstore.smsforwarder.action.RETRY"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
