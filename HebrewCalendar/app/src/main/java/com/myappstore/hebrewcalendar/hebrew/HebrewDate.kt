package com.myappstore.hebrewcalendar.hebrew

import java.time.LocalDate

/**
 * A date in the Hebrew calendar.
 *
 * Months use year order with ICU-compatible indices: 0 = Tishrei ... 4 = Shevat,
 * 5 = Adar I (leap years only), 6 = Adar (Adar II in leap years), 7 = Nisan ...
 * 12 = Elul. The arithmetic is the classic molad-based algorithm (Dershowitz &
 * Reingold, "Calendrical Calculations"), written in plain Kotlin on top of
 * java.time so it runs - and is unit tested - on the JVM without Android/ICU.
 *
 * Dates map to the civil (midnight-to-midnight) day, not sunset-to-sunset.
 */
data class HebrewDate(val year: Int, val month: Int, val day: Int) {

    val isLeapYear: Boolean get() = isLeapYear(year)

    /** @return the Gregorian date this Hebrew date falls on. */
    fun toLocalDate(): LocalDate = LocalDate.ofEpochDay(toEpochDay(year, month, day))

    companion object {
        const val TISHREI = 0
        const val HESHVAN = 1
        const val KISLEV = 2
        const val TEVET = 3
        const val SHEVAT = 4
        const val ADAR_I = 5
        const val ADAR = 6
        const val NISAN = 7
        const val IYAR = 8
        const val SIVAN = 9
        const val TAMMUZ = 10
        const val AV = 11
        const val ELUL = 12

        /** Epoch day (days since 1970-01-01) of 1 Tishrei AM 1. */
        private const val HEBREW_EPOCH_EPOCH_DAY = -2_092_590L

        /** Converts a Gregorian date to its Hebrew date. */
        fun of(date: LocalDate): HebrewDate {
            val epochDay = date.toEpochDay()
            var year = ((epochDay - HEBREW_EPOCH_EPOCH_DAY) / 365.2468).toInt() + 1
            while (newYearEpochDay(year + 1) <= epochDay) year++
            while (newYearEpochDay(year) > epochDay) year--
            var remaining = epochDay - newYearEpochDay(year)
            for (month in monthsOfYear(year)) {
                val length = monthLength(year, month)
                if (remaining < length) return HebrewDate(year, month, remaining.toInt() + 1)
                remaining -= length
            }
            throw IllegalStateException("Date $date fell outside Hebrew year $year")
        }

        /**
         * @return the Gregorian date of [day] in [month] of [year]; a day past the end
         * of the month (e.g. 30 Heshvan in a short year) rolls into the next month.
         */
        fun toLocalDate(year: Int, month: Int, day: Int): LocalDate =
            LocalDate.ofEpochDay(toEpochDay(year, month, day))

        fun isLeapYear(year: Int): Boolean = Math.floorMod(7 * year + 1, 19) < 7

        /** @return the months of [year] in order (Adar I only in leap years). */
        fun monthsOfYear(year: Int): List<Int> =
            (TISHREI..ELUL).filter { it != ADAR_I || isLeapYear(year) }

        fun daysInYear(year: Int): Int = (newYearEpochDay(year + 1) - newYearEpochDay(year)).toInt()

        fun monthLength(year: Int, month: Int): Int = when (month) {
            HESHVAN -> if (daysInYear(year) % 10 == 5) 30 else 29
            KISLEV -> if (daysInYear(year) % 10 == 3) 29 else 30
            TEVET, ADAR, IYAR, TAMMUZ, ELUL -> 29
            else -> 30
        }

        private fun toEpochDay(year: Int, month: Int, day: Int): Long {
            require(month != ADAR_I || isLeapYear(year)) { "Adar I does not exist in $year" }
            val monthsBefore = monthsOfYear(year).takeWhile { it != month }
            return newYearEpochDay(year) + monthsBefore.sumOf { monthLength(year, it) } + day - 1
        }

        private fun newYearEpochDay(year: Int): Long =
            HEBREW_EPOCH_EPOCH_DAY + elapsedDays(year) + yearLengthCorrection(year)

        /** Days from the epoch to the molad of Tishrei, with the "lo ADU" postponement. */
        private fun elapsedDays(year: Int): Long {
            val monthsElapsed = Math.floorDiv(235L * year - 234, 19L)
            val partsElapsed = 12084L + 13753L * monthsElapsed
            val days = 29L * monthsElapsed + Math.floorDiv(partsElapsed, 25920L)
            return if (Math.floorMod(3 * (days + 1), 7L) < 3) days + 1 else days
        }

        /** Remaining postponements that keep year lengths within 353-355 / 383-385. */
        private fun yearLengthCorrection(year: Int): Int {
            val previous = elapsedDays(year - 1)
            val current = elapsedDays(year)
            val next = elapsedDays(year + 1)
            return when {
                next - current == 356L -> 2
                current - previous == 382L -> 1
                else -> 0
            }
        }
    }
}
