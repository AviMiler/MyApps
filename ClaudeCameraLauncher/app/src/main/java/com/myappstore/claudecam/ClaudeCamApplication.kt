package com.myappstore.claudecam

import android.app.Application
import android.content.Intent
import android.util.Log
import kotlin.system.exitProcess

/**
 * Installs a global crash handler so a runtime exception opens a readable
 * error screen (with the full stack trace, selectable for screenshotting)
 * instead of the OS "keeps stopping" crash loop with no diagnostic info.
 */
class ClaudeCamApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = Log.getStackTraceString(throwable)
                val intent = Intent(applicationContext, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, trace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            } catch (inner: Throwable) {
                // Fall through to the default handler below if even the crash screen fails.
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }
}
