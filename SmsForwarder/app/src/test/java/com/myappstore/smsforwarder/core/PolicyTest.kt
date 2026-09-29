package com.myappstore.smsforwarder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PolicyTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun nothingActiveMeansGo() {
        assertEquals(HoldDecision.Go, HoldPolicy.decide(at(2026, 9, 29, 12), zone, HoldRules()))
    }

    @Test
    fun pauseHoldsUntilItEnds() {
        val until = at(2026, 9, 29, 15)
        val decision = HoldPolicy.decide(at(2026, 9, 29, 12), zone, HoldRules(pausedUntil = until))
        assertEquals(HoldDecision.Hold(until, HoldCause.PAUSED), decision)
    }

    @Test
    fun pauseCanDropInstead() {
        val rules = HoldRules(pausedUntil = at(2026, 9, 29, 15), holdWhilePaused = false)
        assertEquals(HoldDecision.Drop(HoldCause.PAUSED), HoldPolicy.decide(at(2026, 9, 29, 12), zone, rules))
        // Messages that were already accepted are never dropped.
        assertTrue(HoldPolicy.decide(at(2026, 9, 29, 12), zone, rules, forceHold = true) is HoldDecision.Hold)
    }

    @Test
    fun indefinitePause() {
        val decision = HoldPolicy.decide(at(2026, 9, 29, 12), zone, HoldRules(pausedUntil = HoldPolicy.FOREVER))
        assertEquals(HoldDecision.Hold(HoldPolicy.FOREVER, HoldCause.PAUSED), decision)
    }

    @Test
    fun quietHoursHoldUntilMorning() {
        val rules = HoldRules(quietHours = TimeWindow(Days.ALL, 22 * 60, 7 * 60))
        val decision = HoldPolicy.decide(at(2026, 9, 29, 23, 15), zone, rules)
        assertEquals(HoldDecision.Hold(at(2026, 9, 30, 7), HoldCause.QUIET_HOURS), decision)
    }

    @Test
    fun overlappingHoldsChain() {
        // Quiet hours end at 07:00 but the pause lasts until 09:00.
        val rules = HoldRules(
            pausedUntil = at(2026, 9, 30, 9),
            quietHours = TimeWindow(Days.ALL, 22 * 60, 7 * 60),
        )
        val decision = HoldPolicy.decide(at(2026, 9, 29, 23), zone, rules)
        assertEquals(HoldDecision.Hold(at(2026, 9, 30, 9), HoldCause.PAUSED), decision)

        // A pause that ends inside quiet hours keeps holding until they end.
        val later = HoldRules(pausedUntil = at(2026, 9, 29, 23), quietHours = TimeWindow(Days.ALL, 22 * 60, 7 * 60))
        assertEquals(
            HoldDecision.Hold(at(2026, 9, 30, 7), HoldCause.PAUSED),
            HoldPolicy.decide(at(2026, 9, 29, 20), zone, later),
        )
    }

    @Test
    fun shabbatAlwaysHoldsAndWins() {
        val rest = RestCalendar(Cities.byId("jerusalem"), zone, 40, 40) { false }
        val rules = HoldRules(pausedUntil = at(2026, 10, 3, 12), holdWhilePaused = false, rest = rest)
        val decision = HoldPolicy.decide(at(2026, 10, 3, 10), zone, rules)
        assertTrue(decision is HoldDecision.Hold)
        decision as HoldDecision.Hold
        assertEquals(HoldCause.REST_DAY, decision.cause)
        val release = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(decision.until), zone)
        assertEquals(3, release.dayOfMonth)
        assertTrue(release.hour in 18..20)
    }

    @Test
    fun senderMatching() {
        val sources = listOf("050-123-4567", "Leumi")
        assertTrue(SenderMatch.matches(SourceMode.SELECTED, sources, emptyList(), "+972501234567", true))
        assertTrue(SenderMatch.matches(SourceMode.SELECTED, sources, emptyList(), "LEUMI", false))
        assertFalse(SenderMatch.matches(SourceMode.SELECTED, sources, emptyList(), "0527654321", true))
        assertTrue(SenderMatch.matches(SourceMode.UNKNOWN, emptyList(), emptyList(), "0527654321", false))
        assertFalse(SenderMatch.matches(SourceMode.UNKNOWN, emptyList(), emptyList(), "0527654321", true))
        assertTrue(SenderMatch.matches(SourceMode.EVERYONE, emptyList(), emptyList(), "x", true))
        assertFalse(SenderMatch.matches(SourceMode.EVERYONE, emptyList(), listOf("0527654321"), "+972527654321", true))
    }

    @Test
    fun contentFilter() {
        assertEquals(ContentVerdict.PASS, ContentFilter.check("שלום", emptyList(), emptyList(), false, null))
        assertEquals(ContentVerdict.PASS, ContentFilter.check("זה דחוף!", listOf("דחוף", "חשוב"), emptyList(), false, null))
        assertEquals(ContentVerdict.MISSING_KEYWORD, ContentFilter.check("שלום", listOf("דחוף"), emptyList(), false, null))
        assertEquals(ContentVerdict.EXCLUDED_KEYWORD, ContentFilter.check("Promo SALE", emptyList(), listOf("sale"), false, null))
        assertEquals(ContentVerdict.NOT_A_CODE, ContentFilter.check("שלום", emptyList(), emptyList(), true, null))
        assertEquals(ContentVerdict.PASS, ContentFilter.check("קוד 1234", emptyList(), emptyList(), true, "1234"))
    }
}
