package com.myappstore.smsforwarder.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** A city used for Shabbat times, with its customary candle-lighting offset. */
data class City(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val candleMinutes: Int,
)

object Cities {
    val all = listOf(
        City("jerusalem", "ירושלים", 31.7683, 35.2137, 40),
        City("tel_aviv", "תל אביב–יפו", 32.0853, 34.7818, 20),
        City("haifa", "חיפה", 32.7940, 34.9896, 30),
        City("beer_sheva", "באר שבע", 31.2518, 34.7913, 20),
        City("bnei_brak", "בני ברק", 32.0807, 34.8338, 20),
        City("petah_tikva", "פתח תקווה", 32.0840, 34.8878, 20),
        City("rishon", "ראשון לציון", 31.9730, 34.7925, 20),
        City("ashdod", "אשדוד", 31.8044, 34.6553, 20),
        City("netanya", "נתניה", 32.3215, 34.8532, 20),
        City("modiin", "מודיעין", 31.8980, 35.0104, 20),
        City("beit_shemesh", "בית שמש", 31.7470, 34.9881, 30),
        City("rehovot", "רחובות", 31.8928, 34.8113, 20),
        City("herzliya", "הרצליה", 32.1624, 34.8447, 20),
        City("raanana", "רעננה", 32.1848, 34.8713, 20),
        City("ashkelon", "אשקלון", 31.6688, 34.5743, 20),
        City("safed", "צפת", 32.9646, 35.4960, 30),
        City("tiberias", "טבריה", 32.7922, 35.5312, 30),
        City("eilat", "אילת", 29.5577, 34.9519, 20),
    )

    val default: City get() = all.first()

    fun byId(id: String?): City = all.firstOrNull { it.id == id } ?: default
}

/**
 * Shabbat and Yom Tov windows: from candle lighting on the eve of the first rest
 * day until nightfall after the last one. Consecutive rest days (for example a
 * holiday next to Shabbat) merge into one window.
 */
class RestCalendar(
    private val city: City,
    private val zone: ZoneId,
    private val candleMinutes: Int,
    private val havdalahMinutes: Int,
    private val isHoliday: (LocalDate) -> Boolean,
) {

    /** [includesHoliday] is true when the window covers a holiday, not only Shabbat. */
    data class Window(
        val start: ZonedDateTime,
        val end: ZonedDateTime,
        val firstDay: LocalDate,
        val lastDay: LocalDate,
        val includesHoliday: Boolean,
    )

    fun isRestDay(day: LocalDate): Boolean = day.dayOfWeek == DayOfWeek.SATURDAY || isHoliday(day)

    /** The window active at [now], if any. */
    fun activeAt(now: ZonedDateTime): Window? {
        val today = now.withZoneSameInstant(zone).toLocalDate()
        for (day in listOf(today, today.plusDays(1))) {
            val window = windowAround(day) ?: continue
            if (!now.isBefore(window.start) && now.isBefore(window.end)) return window
        }
        return null
    }

    /** The active window, or the next one that has not ended yet (searching a few weeks ahead). */
    fun currentOrNext(now: ZonedDateTime): Window? {
        activeAt(now)?.let { return it }
        var day = now.withZoneSameInstant(zone).toLocalDate()
        repeat(SEARCH_DAYS) {
            val window = windowAround(day)
            if (window != null) {
                if (now.isBefore(window.end)) return window
                day = window.lastDay.plusDays(1)
            } else {
                day = day.plusDays(1)
            }
        }
        return null
    }

    private fun windowAround(day: LocalDate): Window? {
        if (!isRestDay(day)) return null
        var first = day
        var last = day
        var guard = 0
        while (guard++ < MAX_BLOCK && isRestDay(first.minusDays(1))) first = first.minusDays(1)
        guard = 0
        while (guard++ < MAX_BLOCK && isRestDay(last.plusDays(1))) last = last.plusDays(1)

        val eveSunset = SolarTimes.sunset(first.minusDays(1), city.latitude, city.longitude, zone) ?: return null
        val lastSunset = SolarTimes.sunset(last, city.latitude, city.longitude, zone) ?: return null
        var holiday = false
        var d = first
        while (!d.isAfter(last)) {
            if (isHoliday(d)) holiday = true
            d = d.plusDays(1)
        }
        return Window(
            start = eveSunset.minusMinutes(candleMinutes.toLong()),
            end = lastSunset.plusMinutes(havdalahMinutes.toLong()),
            firstDay = first,
            lastDay = last,
            includesHoliday = holiday,
        )
    }

    private companion object {
        const val MAX_BLOCK = 6
        const val SEARCH_DAYS = 40
    }
}
