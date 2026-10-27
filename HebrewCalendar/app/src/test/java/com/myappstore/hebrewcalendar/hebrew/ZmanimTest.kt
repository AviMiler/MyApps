package com.myappstore.hebrewcalendar.hebrew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class ZmanimTest {

    private val jerusalem = Cities.named("ירושלים")

    private fun zman(date: LocalDate, name: String, city: City = jerusalem): LocalTime =
        Zmanim.of(date, city).first { it.name == name }.time!!

    private fun assertNear(expected: String, actual: LocalTime, toleranceMinutes: Long = 2) {
        val diff = Duration.between(LocalTime.parse(expected), actual).abs().toMinutes()
        assertTrue("expected ~$expected, got $actual", diff <= toleranceMinutes)
    }

    @Test
    fun jerusalemSummerSolstice() {
        // NOAA solar calculator, sea level, IDT.
        val date = LocalDate.of(2024, 6, 21)
        assertNear("05:33", zman(date, "הנץ החמה"))
        assertNear("19:48", zman(date, "שקיעה"))
    }

    @Test
    fun jerusalemWinterSolstice() {
        val date = LocalDate.of(2024, 12, 21)
        assertNear("06:35", zman(date, "הנץ החמה"))
        assertNear("16:39", zman(date, "שקיעה"))
    }

    @Test
    fun proportionalHoursAreOrdered() {
        val times = Zmanim.of(LocalDate.of(2026, 10, 27), jerusalem).map { it.time!! }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun chatzotIsMidwayBetweenSunriseAndSunset() {
        val date = LocalDate.of(2026, 3, 15)
        val sunrise = zman(date, "הנץ החמה").toSecondOfDay()
        val sunset = zman(date, "שקיעה").toSecondOfDay()
        assertNear(LocalTime.ofSecondOfDay(((sunrise + sunset) / 2).toLong()).toString(), zman(date, "חצות היום"), 1)
    }

    @Test
    fun candleLightingOnFridayByCityCustom() {
        val friday = LocalDate.of(2026, 10, 30)
        val sunset = zman(friday, "שקיעה")
        assertEquals(sunset.minusMinutes(40), zman(friday, "הדלקת נרות"))
        val telAviv = Cities.named("תל אביב")
        assertEquals(zman(friday, "שקיעה", telAviv).minusMinutes(20), zman(friday, "הדלקת נרות", telAviv))
    }

    @Test
    fun shabbatEndOnSaturdayOnly() {
        val names = Zmanim.of(LocalDate.of(2026, 10, 31), jerusalem).map { it.name }
        assertTrue("צאת השבת" in names)
        assertFalse("הדלקת נרות" in names)
        assertFalse("צאת השבת" in Zmanim.of(LocalDate.of(2026, 10, 28), jerusalem).map { it.name })
    }

    @Test
    fun erevYomTovAndItsEnd() {
        // Shavuot 5786 is Friday 2026-05-22 (followed by Shabbat - no end time on the chag itself).
        val erevShavuot = LocalDate.of(2026, 5, 21)
        assertTrue("הדלקת נרות" in Zmanim.of(erevShavuot, jerusalem).map { it.name })
        val shavuot = Zmanim.of(LocalDate.of(2026, 5, 22), jerusalem).map { it.name }
        assertFalse("צאת החג" in shavuot)
        assertFalse("הדלקת נרות" in shavuot)
        // Yom Kippur 5787 is Monday 2026-09-21.
        assertTrue("צאת החג" in Zmanim.of(LocalDate.of(2026, 9, 21), jerusalem).map { it.name })
    }

    @Test
    fun unknownCityFallsBackToDefault() {
        assertEquals(Cities.DEFAULT, Cities.named("לא קיימת"))
        assertEquals(Cities.DEFAULT, Cities.named(null))
    }
}
