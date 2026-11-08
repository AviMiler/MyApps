package com.myappstore.smsforwarder.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunset times using the classic NOAA / Almanac for Computers algorithm
 * (accurate to about a minute at Israeli latitudes).
 */
object SolarTimes {

    /** Official sunset: the sun's upper limb touches the horizon, with refraction. */
    const val ZENITH_OFFICIAL = 90.833

    fun sunset(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        zenith: Double = ZENITH_OFFICIAL,
    ): ZonedDateTime? {
        val dayOfYear = date.dayOfYear
        val lngHour = longitude / 15.0
        val t = dayOfYear + (18.0 - lngHour) / 24.0

        val meanAnomaly = 0.9856 * t - 3.289
        val trueLongitude = normalize(
            meanAnomaly + 1.916 * sinDeg(meanAnomaly) + 0.020 * sinDeg(2 * meanAnomaly) + 282.634,
            360.0,
        )

        var rightAscension = normalize(Math.toDegrees(atan(0.91764 * tanDeg(trueLongitude))), 360.0)
        val lQuadrant = floor(trueLongitude / 90.0) * 90.0
        val raQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + lQuadrant - raQuadrant) / 15.0

        val sinDec = 0.39782 * sinDeg(trueLongitude)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosDeg(zenith) - sinDec * sinDeg(latitude)) / (cosDec * cosDeg(latitude))
        if (cosH > 1.0 || cosH < -1.0) return null

        val hourAngle = Math.toDegrees(acos(cosH)) / 15.0
        val localMean = hourAngle + rightAscension - 0.06571 * t - 6.622
        val utcHours = normalize(localMean - lngHour, 24.0)

        val utc = date.atStartOfDay(ZoneOffset.UTC).plusSeconds((utcHours * 3600.0).roundToLong())
        var local = utc.withZoneSameInstant(zone)
        if (local.toLocalDate().isAfter(date)) local = local.minusDays(1)
        if (local.toLocalDate().isBefore(date)) local = local.plusDays(1)
        return local
    }

    private fun normalize(value: Double, range: Double): Double {
        var v = value % range
        if (v < 0) v += range
        return v
    }

    private fun sinDeg(d: Double) = sin(Math.toRadians(d))
    private fun cosDeg(d: Double) = cos(Math.toRadians(d))
    private fun tanDeg(d: Double) = tan(Math.toRadians(d))
}
