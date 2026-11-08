package com.myappstore.smsforwarder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonesTest {

    @Test
    fun canonicalRewritesIsraeliInternationalFormats() {
        assertEquals("0501234567", Phones.canonical("+972 50-123-4567"))
        assertEquals("0501234567", Phones.canonical("00972501234567"))
        assertEquals("0501234567", Phones.canonical("972501234567"))
        assertEquals("0501234567", Phones.canonical("050-1234567"))
        assertEquals("021234567", Phones.canonical("+972-2-1234567"))
    }

    @Test
    fun sameNumberInDifferentFormats() {
        assertTrue(Phones.same("+972501234567", "050-123-4567"))
        assertTrue(Phones.same("02-1234567", "+97221234567"))
        assertTrue(Phones.same("+1 (212) 555-0100", "12125550100"))
    }

    @Test
    fun differentNumbersDoNotMatch() {
        assertFalse(Phones.same("0501234567", "0521234567"))
        assertFalse(Phones.same("0501234567", "0501234568"))
    }

    @Test
    fun namedSendersMatchCaseInsensitively() {
        assertTrue(Phones.same("Leumi", "LEUMI"))
        assertTrue(Phones.same("Bank-Hapoalim", "bank hapoalim"))
        assertFalse(Phones.same("Leumi", "Leumi Card"))
        assertTrue(Phones.same("8888", "8888"))
        assertFalse(Phones.same("Leumi", "0501234567"))
    }

    @Test
    fun phoneLikeDetection() {
        assertTrue(Phones.isPhoneLike("+972 50 123 4567"))
        assertFalse(Phones.isPhoneLike("8888"))
        assertFalse(Phones.isPhoneLike("Leumi"))
        assertFalse(Phones.isPhoneLike("   "))
    }

    @Test
    fun prettyFormatting() {
        assertEquals("050-123-4567", Phones.pretty("+972501234567"))
        assertEquals("02-123-4567", Phones.pretty("021234567"))
        assertEquals("Leumi", Phones.pretty(" Leumi "))
        assertEquals("8888", Phones.pretty("8888"))
    }

    @Test
    fun searchQueries() {
        assertTrue(Phones.matchesQuery("+972501234567", "0501"))
        assertTrue(Phones.matchesQuery("+972501234567", "1234"))
        assertTrue(Phones.matchesQuery("Leumi", "leu"))
        assertFalse(Phones.matchesQuery("+972501234567", "999"))
        assertTrue(Phones.matchesQuery("anything", ""))
    }

    @Test
    fun keysDeduplicate() {
        assertEquals(Phones.key("+972501234567"), Phones.key("050-1234567"))
        assertEquals(Phones.key("Leumi"), Phones.key("LEUMI"))
    }
}
