package com.myappstore.smsforwarder.core

import java.time.DayOfWeek
import java.time.LocalDateTime

/** Days of the week as a bit mask, Sunday = bit 0 (the Israeli week starts on Sunday). */
object Days {
    const val ALL = 0b1111111
    const val SUN_TO_THU = 0b0011111
    const val SUN_TO_FRI = 0b0111111

    fun index(day: DayOfWeek): Int = day.value % 7

    fun has(mask: Int, day: DayOfWeek): Boolean = mask and (1 shl index(day)) != 0

    fun toggle(mask: Int, index: Int): Int = mask xor (1 shl index)

    fun contains(mask: Int, index: Int): Boolean = mask and (1 shl index) != 0
}

/**
 * A daily time range on selected days. A range whose end is not after its start
 * crosses midnight (22:00-07:00); the days refer to the day the range starts.
 * Equal start and end mean the whole day.
 */
data class TimeWindow(val days: Int, val startMinute: Int, val endMinute: Int) {

    val lengthMinutes: Int
        get() = if (endMinute > startMinute) endMinute - startMinute else endMinute + MINUTES_PER_DAY - startMinute

    /** The start/end of the occurrence that contains [t], or null when [t] is outside the window. */
    fun occurrenceAt(t: LocalDateTime): Pair<LocalDateTime, LocalDateTime>? {
        if (days == 0) return null
        for (back in 0L..1L) {
            val day = t.toLocalDate().minusDays(back)
            if (!Days.has(days, day.dayOfWeek)) continue
            val start = day.atStartOfDay().plusMinutes(startMinute.toLong())
            val end = start.plusMinutes(lengthMinutes.toLong())
            if (!t.isBefore(start) && t.isBefore(end)) return start to end
        }
        return null
    }

    fun contains(t: LocalDateTime): Boolean = occurrenceAt(t) != null

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
