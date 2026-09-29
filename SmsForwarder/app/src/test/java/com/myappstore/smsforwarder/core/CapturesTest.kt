package com.myappstore.smsforwarder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturesTest {

    private val t = 1_700_000_000_000L
    private val minute = 60_000L

    @Test
    fun notificationOfAnSmsAlreadyHandledIsDropped() {
        val log = CaptureLog()
        assertTrue(log.claim(Origin.SMS, "+972501234567", "אבא", "אני בדרך", t))
        assertFalse(log.claim(Origin.NOTIFICATION, "0501234567", "אבא", "אני  בדרך\n", t + 8_000))
    }

    @Test
    fun smsArrivingAfterItsNotificationIsDropped() {
        val log = CaptureLog()
        assertTrue(log.claim(Origin.NOTIFICATION, "אבא", "אבא", "אני בדרך", t))
        assertFalse(log.claim(Origin.SMS, "0501234567", "אבא", "אני בדרך", t + 20_000))
    }

    @Test
    fun missedSmsIsCaughtFromTheNotification() {
        val log = CaptureLog()
        assertTrue(log.claim(Origin.SMS, "0501234567", "אבא", "הודעה ראשונה", t))
        assertTrue(log.claim(Origin.NOTIFICATION, "אבא", "אבא", "הודעה שנייה", t + 8_000))
    }

    @Test
    fun repeatsOnTheSamePathAreSeparateMessages() {
        val log = CaptureLog()
        assertTrue(log.claim(Origin.SMS, "0501234567", null, "OK", t))
        assertTrue(log.claim(Origin.SMS, "0501234567", null, "OK", t + minute))
        // Each SMS absorbs exactly one notification copy.
        assertFalse(log.claim(Origin.NOTIFICATION, "0501234567", null, "OK", t + minute + 5_000))
        assertFalse(log.claim(Origin.NOTIFICATION, "0501234567", null, "OK", t + minute + 6_000))
        assertTrue(log.claim(Origin.NOTIFICATION, "0501234567", null, "OK", t + minute + 7_000))
    }

    @Test
    fun copiesFarApartInTimeAreDifferentMessages() {
        val log = CaptureLog()
        assertTrue(log.claim(Origin.SMS, "0501234567", "אבא", "תתקשר", t))
        assertTrue(log.claim(Origin.NOTIFICATION, "אבא", "אבא", "תתקשר", t + 20 * minute))
    }

    @Test
    fun differentSendersWithTheSameTextAreDifferentMessages() {
        val log = CaptureLog()
        assertTrue(log.claim(Origin.SMS, "0501234567", "אבא", "תודה", t))
        assertTrue(log.claim(Origin.NOTIFICATION, "0527654321", "סבא", "תודה", t + 5_000))
        assertTrue(log.claim(Origin.NOTIFICATION, "סבא", "סבא", "תודה", t + 6_000))
    }

    @Test
    fun truncatedNotificationTextMatchesTheFullSms() {
        val log = CaptureLog()
        assertTrue(log.claim(Origin.SMS, "Leumi", null, "קוד האימות שלך הוא 482913. אין למסור אותו לאף אחד.", t))
        assertFalse(log.claim(Origin.NOTIFICATION, "Leumi", "Leumi", "קוד האימות שלך הוא 482913. אין…", t + 3_000))
    }

    @Test
    fun namedSendersCompareByName() {
        assertTrue(CaptureLog.sameSender("LEUMI", null, "Leumi", "Leumi"))
        assertTrue(CaptureLog.sameSender("0501234567", "אבא", "אבא", "\u200Fאבא "))
        assertFalse(CaptureLog.sameSender("0501234567", "אבא", "אמא", "אמא"))
        // Only a number on one side and only a name on the other: cannot tell, assume the same.
        assertTrue(CaptureLog.sameSender("0501234567", null, "אבא", "אבא"))
    }

    @Test
    fun seenKeysRememberAndExpire() {
        val seen = SeenKeys(maxAgeMs = 10 * minute, maxSize = 3)
        val key = SeenKeys.keyOf("com.samsung.android.messaging", "אבא", t, "שלום")
        assertTrue(seen.firstSighting(key, t))
        assertFalse(seen.firstSighting(key, t + minute))
        assertTrue(seen.firstSighting(key, t + 20 * minute))
    }

    @Test
    fun seenKeysSurviveARestart() {
        val seen = SeenKeys()
        val key = SeenKeys.keyOf("app", "אבא", t, "שלום")
        seen.firstSighting(key, t)
        val restored = SeenKeys.parse(seen.serialize())
        assertFalse(restored.firstSighting(key, t + minute))
        assertEquals("", SeenKeys().serialize())
        assertTrue(SeenKeys.parse("").firstSighting(key, t))
    }
}
