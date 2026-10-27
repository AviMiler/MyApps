package com.myappstore.hebrewcalendar.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.util.Log
import com.myappstore.hebrewcalendar.model.CalendarEvent
import com.myappstore.hebrewcalendar.model.EventDays
import com.myappstore.hebrewcalendar.model.WritableCalendar
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads and writes the Android calendar provider. Google Calendar accounts on the
 * device are synced into this provider by the system, so this is how the app
 * "syncs with Google" without its own OAuth client: events added here are uploaded
 * by the Google sync adapter, and changes made on the web come back the same way.
 *
 * All methods do I/O - call them off the main thread (the widget's are short).
 */
class CalendarRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    fun canRead(): Boolean = isGranted(Manifest.permission.READ_CALENDAR)

    fun canWrite(): Boolean = isGranted(Manifest.permission.WRITE_CALENDAR)

    /** @return visible events in [first]..[last] by day; empty without permission. */
    fun eventsByDay(first: LocalDate, last: LocalDate): Map<LocalDate, List<CalendarEvent>> {
        if (!canRead()) return emptyMap()
        val zone = ZoneId.systemDefault()
        // One extra day each side: all-day events are stored as UTC midnights.
        val begin = first.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = last.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, begin); ContentUris.appendId(it, end) }
            .build()
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
            Instances.ALL_DAY, Instances.DISPLAY_COLOR, Instances.CALENDAR_DISPLAY_NAME, Instances.EVENT_LOCATION
        )
        val events = mutableListOf<CalendarEvent>()
        try {
            resolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    events += CalendarEvent(
                        id = cursor.getLong(0),
                        title = cursor.getString(1).orEmpty().ifBlank { "(ללא כותרת)" },
                        beginMillis = cursor.getLong(2),
                        endMillis = cursor.getLong(3),
                        isAllDay = cursor.getInt(4) == 1,
                        color = cursor.getInt(5),
                        calendarName = cursor.getString(6).orEmpty(),
                        location = cursor.getString(7).orEmpty()
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Reading events failed (permission revoked?)", e)
        }
        return EventDays.groupByDay(events, zone, first, last)
    }

    /** @return calendars the user can add events to, Google primary calendars first. */
    fun writableCalendars(): List<WritableCalendar> {
        if (!canRead()) return emptyList()
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE, Calendars.IS_PRIMARY
        )
        val selection = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${Calendars.VISIBLE} = 1"
        val args = arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val calendars = mutableListOf<Pair<WritableCalendar, Boolean>>()
        try {
            resolver.query(Calendars.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calendar = WritableCalendar(
                        id = cursor.getLong(0),
                        displayName = cursor.getString(1).orEmpty(),
                        accountName = cursor.getString(2).orEmpty(),
                        isGoogle = cursor.getString(3) == GOOGLE_ACCOUNT_TYPE
                    )
                    calendars += calendar to (cursor.getInt(4) == 1)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Listing calendars failed", e)
        }
        return calendars.sortedWith(compareBy({ !it.first.isGoogle }, { !it.second })).map { it.first }
    }

    /**
     * Adds an event on each of [dates] (one per year for Hebrew-date recurrence).
     * @param time null for an all-day event, otherwise the start of a one-hour event.
     * @return how many events were written.
     */
    fun insertEvents(calendarId: Long, title: String, description: String, dates: List<LocalDate>, time: LocalTime?): Int {
        val zone = ZoneId.systemDefault()
        return dates.count { date ->
            val values = ContentValues().apply {
                put(Events.CALENDAR_ID, calendarId)
                put(Events.TITLE, title)
                put(Events.DESCRIPTION, description)
                if (time == null) {
                    put(Events.ALL_DAY, 1)
                    put(Events.EVENT_TIMEZONE, "UTC")
                    put(Events.DTSTART, date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                    put(Events.DTEND, date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                } else {
                    val start = date.atTime(time).atZone(zone)
                    put(Events.EVENT_TIMEZONE, zone.id)
                    put(Events.DTSTART, start.toInstant().toEpochMilli())
                    put(Events.DTEND, start.plusHours(1).toInstant().toEpochMilli())
                }
            }
            try {
                resolver.insert(Events.CONTENT_URI, values) != null
            } catch (e: SecurityException) {
                Log.e(TAG, "Inserting event on $date failed", e)
                false
            }
        }
    }

    /** Asks the system to sync every Google calendar account now. @return accounts asked. */
    fun requestGoogleSync(): Int {
        val accounts = writableCalendars().filter { it.isGoogle }.map { it.accountName }.distinct()
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        return accounts.count { name ->
            try {
                ContentResolver.requestSync(Account(name, GOOGLE_ACCOUNT_TYPE), CalendarContract.AUTHORITY, extras)
                true
            } catch (e: SecurityException) {
                Log.e(TAG, "Sync request for $name was refused", e)
                false
            }
        }
    }

    private fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "CalendarRepository"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
