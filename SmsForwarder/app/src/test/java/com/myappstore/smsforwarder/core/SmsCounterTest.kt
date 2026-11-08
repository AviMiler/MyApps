package com.myappstore.smsforwarder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCounterTest {

    @Test
    fun gsmText() {
        val c = SmsCounter.count("a".repeat(160))
        assertEquals(1, c.parts)
        assertFalse(c.unicode)
        assertEquals(2, SmsCounter.count("a".repeat(161)).parts)
        assertEquals(3, SmsCounter.count("a".repeat(307)).parts)
    }

    @Test
    fun extendedCharactersCountDouble() {
        assertEquals(162, SmsCounter.count("€" + "a".repeat(160)).units)
    }

    @Test
    fun hebrewIsUnicode() {
        val c = SmsCounter.count("ש".repeat(70))
        assertTrue(c.unicode)
        assertEquals(1, c.parts)
        assertEquals(2, SmsCounter.count("ש".repeat(71)).parts)
        assertEquals(3, SmsCounter.count("ש".repeat(135)).parts)
    }

    @Test
    fun emptyText() {
        assertEquals(0, SmsCounter.count("").parts)
    }
}
