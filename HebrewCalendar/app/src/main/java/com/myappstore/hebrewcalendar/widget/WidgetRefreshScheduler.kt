package com.myappstore.hebrewcalendar.widget

import android.app.AlarmManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import com.myappstore.hebrewcalendar.calendar.CalendarRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps widgets current without a running app: an alarm just after midnight
 * moves "today", and a content-trigger job redraws when the calendar provider
 * changes (e.g. the Google sync adapter downloads new events).
 */
object WidgetRefreshScheduler {

    private const val CALENDAR_CHANGE_JOB_ID = 4201

    fun schedule(context: Context) {
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
        // Inexact RTC: delivered when the device next wakes, which is all a widget needs.
        context.getSystemService(AlarmManager::class.java).set(
            AlarmManager.RTC,
            nextMidnight.toInstant().toEpochMilli() + 60_000,
            MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_REFRESH)
        )
        if (CalendarRepository(context).canRead()) scheduleCalendarChangeJob(context)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(MonthWidgetProvider.broadcast(context, MonthWidgetProvider.ACTION_REFRESH))
        context.getSystemService(JobScheduler::class.java).cancel(CALENDAR_CHANGE_JOB_ID)
    }

    /** Content-trigger jobs fire once, so [CalendarChangeJobService] calls back into [schedule]. */
    private fun scheduleCalendarChangeJob(context: Context) {
        val job = JobInfo.Builder(CALENDAR_CHANGE_JOB_ID, ComponentName(context, CalendarChangeJobService::class.java))
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS)
            )
            .setTriggerContentUpdateDelay(5_000)
            .setTriggerContentMaxDelay(60_000)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }
}
