package com.myappstore.hebrewcalendar.hebrew

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** A place zmanim are computed for; [candleLightingMinutes] before sunset is the local custom. */
data class City(val name: String, val latitude: Double, val longitude: Double, val candleLightingMinutes: Int = 20)

object Cities {
    val ALL = listOf(
        City("ירושלים", 31.7683, 35.2137, 40),
        City("תל אביב", 32.0853, 34.7818),
        City("חיפה", 32.7940, 34.9896, 30),
        City("באר שבע", 31.2518, 34.7913),
        City("בני ברק", 32.0807, 34.8338),
        City("פתח תקווה", 32.0840, 34.8878, 30),
        City("אשדוד", 31.8044, 34.6553),
        City("נתניה", 32.3215, 34.8532),
        City("ראשון לציון", 31.9730, 34.7925),
        City("רחובות", 31.8928, 34.8113),
        City("בית שמש", 31.7470, 34.9881),
        City("מודיעין עילית", 31.9330, 35.0410),
        City("אלעד", 32.0522, 34.9510),
        City("צפת", 32.9646, 35.4960),
        City("טבריה", 32.7959, 35.5310),
        City("אילת", 29.5577, 34.9519)
    )
    val DEFAULT = ALL.first()

    fun named(name: String?): City = ALL.firstOrNull { it.name == name } ?: DEFAULT
}

/**
 * One line of the day's zmanim; [time] is null when the sun never reaches that angle.
 * [isShabbatTime] marks candle lighting and the end of Shabbat or Yom Tov.
 */
data class Zman(val name: String, val time: LocalTime?, val isShabbatTime: Boolean = false)

/**
 * Halachic times of a day, computed with NOAA's solar position formulas at sea
 * level (no elevation correction, like most printed Israeli calendars).
 * Proportional hours: GRA from sunrise to sunset, MGA from dawn to nightfall at 16.1°.
 */
object Zmanim {

    val ISRAEL: ZoneId = ZoneId.of("Asia/Jerusalem")

    private const val SUNRISE = -0.833
    private const val DAWN = -16.1
    private const val MISHEYAKIR = -11.5
    private const val NIGHTFALL = -8.5

    fun of(date: LocalDate, city: City, zone: ZoneId = ISRAEL): List<Zman> {
        fun at(altitude: Double, morning: Boolean) = utcMinutes(date, city, altitude, morning)
        val dawn = at(DAWN, true)
        val sunrise = at(SUNRISE, true)
        val sunset = at(SUNRISE, false)
        val nightfall = at(NIGHTFALL, false)
        val mgaNight = at(DAWN, false)
        fun gra(hours: Double) = proportional(sunrise, sunset, hours)
        fun mga(hours: Double) = proportional(dawn, mgaNight, hours)
        fun time(utcMinutes: Double?) = utcMinutes?.let { local(date, it, zone) }

        val list = mutableListOf(
            Zman("עלות השחר", time(dawn)),
            Zman("זמן טלית ותפילין", time(at(MISHEYAKIR, true))),
            Zman("הנץ החמה", time(sunrise)),
            Zman("סוף זמן ק״ש (מג״א)", time(mga(3.0))),
            Zman("סוף זמן ק״ש (גר״א)", time(gra(3.0))),
            Zman("סוף זמן תפילה (מג״א)", time(mga(4.0))),
            Zman("סוף זמן תפילה (גר״א)", time(gra(4.0))),
            Zman("חצות היום", time(gra(6.0))),
            Zman("מנחה גדולה", time(gra(6.5))),
            Zman("מנחה קטנה", time(gra(9.5))),
            Zman("פלג המנחה", time(gra(10.75))),
            Zman("שקיעה", time(sunset)),
            Zman("צאת הכוכבים", time(nightfall))
        )

        // Shabbat and Yom Tov: candle lighting before the first rest day, its end after the last.
        val holidays = JewishHolidays.between(date, date.plusDays(1))
        fun isRestDay(day: LocalDate) =
            day.dayOfWeek == DayOfWeek.SATURDAY || holidays[day].orEmpty().any { it.isYomTov }
        val restToday = isRestDay(date)
        val restTomorrow = isRestDay(date.plusDays(1))
        if (!restToday && restTomorrow) {
            list.add(list.size - 2, Zman("הדלקת נרות", time(sunset?.minus(city.candleLightingMinutes)), isShabbatTime = true))
        }
        if (restToday && !restTomorrow) {
            val name = if (date.dayOfWeek == DayOfWeek.SATURDAY) "צאת השבת" else "צאת החג"
            list += Zman(name, time(nightfall), isShabbatTime = true)
            list += Zman("$name (ר״ת)", time(sunset?.plus(72)), isShabbatTime = true)
        }
        return list
    }

    private fun proportional(start: Double?, end: Double?, hours: Double): Double? {
        if (start == null || end == null) return null
        return start + (end - start) / 12 * hours
    }

    /** [utcMinutes] after UTC midnight of [date], rounded to the minute, as a local time. */
    private fun local(date: LocalDate, utcMinutes: Double, zone: ZoneId): LocalTime =
        date.atStartOfDay(ZoneOffset.UTC).plusMinutes(utcMinutes.roundToLong()).withZoneSameInstant(zone).toLocalTime()

    /**
     * Minutes after UTC midnight of [date] at which the sun's center is at
     * [altitude] degrees, before noon if [morning]; null if it never gets there.
     * Iterates so the solar position is taken at the event's own time.
     */
    internal fun utcMinutes(date: LocalDate, city: City, altitude: Double, morning: Boolean): Double? {
        var minutes = 720.0 - 4 * city.longitude
        repeat(3) {
            val sun = SolarPosition(date, minutes)
            val cosH = (sin(rad(altitude)) - sin(rad(city.latitude)) * sin(sun.declination)) /
                (cos(rad(city.latitude)) * cos(sun.declination))
            if (cosH < -1 || cosH > 1) return null
            val hourAngle = Math.toDegrees(acos(cosH))
            val noon = 720 - 4 * city.longitude - sun.equationOfTime
            minutes = if (morning) noon - 4 * hourAngle else noon + 4 * hourAngle
        }
        return minutes
    }

    /** Declination (radians) and equation of time (minutes), per NOAA's solar calculator. */
    private class SolarPosition(date: LocalDate, utcMinutes: Double) {
        val declination: Double
        val equationOfTime: Double

        init {
            val julianDay = date.toEpochDay() + 2440587.5 + utcMinutes / 1440
            val t = (julianDay - 2451545) / 36525
            val meanLongitude = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
            val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
            val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
            val center = sin(rad(meanAnomaly)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(rad(2 * meanAnomaly)) * (0.019993 - 0.000101 * t) +
                sin(rad(3 * meanAnomaly)) * 0.000289
            val omega = 125.04 - 1934.136 * t
            val apparentLongitude = meanLongitude + center - 0.00569 - 0.00478 * sin(rad(omega))
            val meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
            val obliquity = meanObliquity + 0.00256 * cos(rad(omega))
            declination = asin(sin(rad(obliquity)) * sin(rad(apparentLongitude)))
            val y = tan(rad(obliquity / 2)).let { it * it }
            val l0 = rad(meanLongitude)
            val m = rad(meanAnomaly)
            equationOfTime = 4 * Math.toDegrees(
                y * sin(2 * l0) - 2 * eccentricity * sin(m) +
                    4 * eccentricity * y * sin(m) * cos(2 * l0) -
                    0.5 * y * y * sin(4 * l0) - 1.25 * eccentricity * eccentricity * sin(2 * m)
            )
        }
    }

    private fun rad(degrees: Double) = Math.toRadians(degrees)
}
