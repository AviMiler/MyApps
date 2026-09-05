package com.myappstore.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms every enabled alarm after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
