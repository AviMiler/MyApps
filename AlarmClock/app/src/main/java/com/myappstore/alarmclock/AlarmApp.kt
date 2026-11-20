package com.myappstore.alarmclock

import android.app.Application

/** Logs process start and any uncaught crash, so a dead alarm pipeline leaves a trace. */
class AlarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            AppLog.e(this, "CRASH", "Uncaught exception in thread ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
        AppLog.i(this, "App", "Process started (pid ${android.os.Process.myPid()})")
    }
}
