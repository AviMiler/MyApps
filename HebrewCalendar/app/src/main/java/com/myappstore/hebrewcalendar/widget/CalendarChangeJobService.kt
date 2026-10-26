package com.myappstore.hebrewcalendar.widget

import android.app.job.JobParameters
import android.app.job.JobService

/** Runs when calendar data changed; redraws widgets (which re-arms the trigger). */
class CalendarChangeJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        MonthWidgetProvider.refreshAll(this)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
