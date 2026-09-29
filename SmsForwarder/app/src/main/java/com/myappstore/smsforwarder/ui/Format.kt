package com.myappstore.smsforwarder.ui

import android.content.Context
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.core.Days
import com.myappstore.smsforwarder.core.HoldPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Hebrew date/time formatting used across the UI. */
object Fmt {

    val dayLetters = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")
    private val dayNames = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormat = DateTimeFormatter.ofPattern("d.M")

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    fun time(ms: Long): String = timeFormat.format(zoned(ms))

    fun minutes(total: Int): String = String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60)

    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    fun dayName(date: LocalDate): String = dayNames[dayIndex(date)]

    /** "א׳–ה׳", "כל יום", or a list of day letters. */
    fun days(context: Context, mask: Int): String = when (mask) {
        Days.ALL -> context.getString(R.string.days_all)
        Days.SUN_TO_THU -> "א׳–ה׳"
        Days.SUN_TO_FRI -> "א׳–ו׳"
        0 -> context.getString(R.string.days_none)
        else -> (0..6).filter { Days.contains(mask, it) }.joinToString(" ") { dayLetters[it] + "׳" }
    }

    /** "היום", "אתמול", or "יום שלישי, 23.9". */
    fun dayLabel(context: Context, date: LocalDate, today: LocalDate): String = when (date) {
        today -> context.getString(R.string.day_today)
        today.minusDays(1) -> context.getString(R.string.day_yesterday)
        else -> context.getString(R.string.day_named, dayName(date), dateFormat.format(date))
    }

    /** Short relative time: "עכשיו", "לפני 5 דק׳", "אתמול 14:02", "3.9 14:02". */
    fun ago(context: Context, ms: Long, now: Long): String {
        val minutes = ChronoUnit.MINUTES.between(Instant.ofEpochMilli(ms), Instant.ofEpochMilli(now))
        val res = context.resources
        return when {
            minutes < 1 -> context.getString(R.string.ago_now)
            minutes < 60 -> res.getQuantityString(R.plurals.ago_minutes, minutes.toInt(), minutes.toInt())
            minutes < 6 * 60 -> {
                val hours = (minutes / 60).toInt()
                res.getQuantityString(R.plurals.ago_hours, hours, hours)
            }
            else -> at(context, ms, now)
        }
    }

    /** A moment in the near future or past: "14:02", "מחר 08:00", "ביום שבת 19:42", "3.10 08:00". */
    fun at(context: Context, ms: Long, now: Long): String {
        val date = zoned(ms).toLocalDate()
        val today = zoned(now).toLocalDate()
        val time = time(ms)
        return when (date) {
            today -> time
            today.plusDays(1) -> context.getString(R.string.at_tomorrow, time)
            today.minusDays(1) -> context.getString(R.string.at_yesterday, time)
            else -> if (date.isAfter(today) && date.isBefore(today.plusDays(7))) {
                context.getString(R.string.at_weekday, dayName(date), time)
            } else {
                context.getString(R.string.at_date, dateFormat.format(date), time)
            }
        }
    }

    /** "עד 18:30", "עד מחר 08:00" or "עד שתחזירו" for an open-ended pause. */
    fun until(context: Context, ms: Long, now: Long): String =
        if (ms == HoldPolicy.FOREVER) {
            context.getString(R.string.until_resumed)
        } else {
            context.getString(R.string.until_time, at(context, ms, now))
        }

    fun startOfToday(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    /** The week starts on Sunday. */
    fun startOfWeek(): Long {
        val today = LocalDate.now(zone)
        return today.minusDays(dayIndex(today).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Tomorrow (or later today) at [hour]:00 - used for "pause until morning". */
    fun nextMorning(now: Long, hour: Int = 7): Long {
        val current = zoned(now)
        var target = current.toLocalDate().atTime(hour, 0).atZone(zone)
        if (!target.isAfter(current)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }
}
