package com.myappstore.alarmclock

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/**
 * Tiny file logger. Every important step of the alarm pipeline is appended to
 * `files/logs/alarm_log.txt` (capped in size) so a missed alarm can be diagnosed
 * afterwards. [buildShareIntent] bundles the log with a device/permission snapshot
 * into one text file the user can send.
 */
object AppLog {

    private const val DIR = "logs"
    private const val LOG_NAME = "alarm_log.txt"
    private const val SHARE_NAME = "alarm_diagnostics.txt"
    private const val MAX_BYTES = 400_000L
    private val lock = Any()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun i(context: Context, tag: String, message: String) = write(context, "I", tag, message, null)

    fun e(context: Context, tag: String, message: String, t: Throwable? = null) =
        write(context, "E", tag, message, t)

    fun formatTime(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(formatter)

    private fun write(context: Context, level: String, tag: String, message: String, t: Throwable?) {
        if (level == "E") Log.e("AlarmClock/$tag", message, t) else Log.i("AlarmClock/$tag", message)
        runCatching {
            synchronized(lock) {
                val file = logFile(context)
                if (file.length() > MAX_BYTES) {
                    file.writeText(file.readText().takeLast((MAX_BYTES / 2).toInt()))
                }
                val sb = StringBuilder()
                sb.append(LocalDateTime.now().format(formatter))
                    .append(' ').append(level).append('/').append(tag)
                    .append(": ").append(message).append('\n')
                if (t != null) sb.append(stackTrace(t))
                file.appendText(sb.toString())
            }
        }
    }

    private fun stackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun logDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    private fun logFile(context: Context): File = File(logDir(context), LOG_NAME)

    /** A chooser that sends the diagnostics file (snapshot + alarms + exit history + log). */
    fun buildShareIntent(context: Context): Intent {
        val body = StringBuilder()
        body.append("=== AlarmClock diagnostics ===\n")
        body.append(snapshot(context))
        body.append("\n=== Alarms ===\n")
        runCatching {
            AlarmStore.all(context).forEach { a ->
                body.append(
                    "id=${a.id} ${"%02d:%02d".format(a.hour, a.minute)} days=${a.days.sorted()} " +
                        "enabled=${a.enabled} ring=${a.ringSeconds}s vol=${a.volumePercent} " +
                        "override=${a.overrideSystemSound} vibrate=${a.vibrate} sound=${a.soundUri} " +
                        "next=${if (a.enabled) formatTime(a.nextTriggerMillis()) else "-"}\n"
                )
            }
        }
        body.append("\n=== Process exit history (newest first) ===\n")
        body.append(exitHistory(context))
        body.append("\n=== Log ===\n")
        synchronized(lock) {
            val f = logFile(context)
            if (f.exists()) body.append(f.readText())
        }

        val out = File(logDir(context), SHARE_NAME)
        out.writeText(body.toString())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", out)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AlarmClock diagnostics")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.share_log))
    }

    /** Current device state that decides whether an alarm can ring. */
    fun snapshot(context: Context): String {
        val sb = StringBuilder()
        fun line(key: String, value: () -> Any?) {
            sb.append(key).append(": ")
                .append(runCatching { value() }.getOrElse { "error: ${it.message}" })
                .append('\n')
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        line("Now") { "${formatTime(System.currentTimeMillis())} (${TimeZone.getDefault().id})" }
        line("Device") { "${Build.MANUFACTURER} ${Build.MODEL}" }
        line("Android") { "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }
        line("App version") {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (code ${info.longVersionCode})"
        }
        line("Exact alarms allowed") { AlarmScheduler.canScheduleExact(context) }
        line("Notifications enabled") { NotificationManagerCompat.from(context).areNotificationsEnabled() }
        line("Full-screen intent allowed") {
            if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else "n/a (below API 34)"
        }
        line("DnD policy access") { nm.isNotificationPolicyAccessGranted }
        line("DnD filter (1=all 2=priority 3=none 4=alarms)") { nm.currentInterruptionFilter }
        line("Ignoring battery optimizations") { pm.isIgnoringBatteryOptimizations(context.packageName) }
        line("Power save mode") { pm.isPowerSaveMode }
        line("Standby bucket") {
            if (Build.VERSION.SDK_INT >= 28) {
                val bucket = context.getSystemService(UsageStatsManager::class.java).appStandbyBucket
                when (bucket) {
                    10 -> "ACTIVE"
                    20 -> "WORKING_SET"
                    30 -> "FREQUENT"
                    40 -> "RARE"
                    45 -> "RESTRICTED"
                    else -> bucket.toString()
                }
            } else "n/a"
        }
        line("Alarm stream volume") {
            "${audio.getStreamVolume(AudioManager.STREAM_ALARM)}/${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}"
        }
        line("Ringer mode (0=silent 1=vibrate 2=normal)") { audio.ringerMode }
        line("System next-alarm-clock") {
            alarmManager.nextAlarmClock?.triggerTime?.let { formatTime(it) } ?: "none"
        }
        return sb.toString()
    }

    private fun exitHistory(context: Context): String {
        if (Build.VERSION.SDK_INT < 30) return "n/a (below API 30)\n"
        return runCatching {
            val am = context.getSystemService(ActivityManager::class.java)
            val items = am.getHistoricalProcessExitReasons(context.packageName, 0, 15)
            if (items.isEmpty()) return@runCatching "none recorded\n"
            items.joinToString("\n", postfix = "\n") {
                "${formatTime(it.timestamp)} reason=${exitReasonName(it.reason)} " +
                    "importance=${it.importance} ${it.description ?: ""}"
            }
        }.getOrElse { "error: ${it.message}\n" }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED (force stop)"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> reason.toString()
    }
}
