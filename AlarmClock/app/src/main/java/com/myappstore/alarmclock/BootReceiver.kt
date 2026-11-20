package com.myappstore.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms every enabled alarm whenever the system may have dropped or shifted them:
 * reboot, app update, clock or time-zone change, and when the exact-alarm permission
 * is granted or revoked (the system cancels all exact alarms on revoke).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i(context, "System", "broadcast received: ${intent.action}")
        AlarmScheduler.rescheduleAll(context, intent.action ?: "system event")
    }
}
