package com.myappstore.smsforwarder.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HebrewTest {

    @Test
    fun hebrewWordsJoinDirectly() {
        assertEquals("אבא", Hebrew.afterPrefix("אבא"))
        assertEquals("המשרד", Hebrew.afterPrefix("המשרד"))
    }

    @Test
    fun digitsAndForeignLettersTakeAHyphen() {
        assertEquals("-Leumi", Hebrew.afterPrefix("Leumi"))
        assertEquals("-050-123-4567", Hebrew.afterPrefix("050-123-4567"))
        assertEquals("-+972 50-123-4567", Hebrew.afterPrefix("+972 50-123-4567"))
    }

    @Test
    fun invisibleMarksAreSkipped() {
        assertEquals("\u200Fאבא", Hebrew.afterPrefix("\u200Fאבא"))
        assertEquals("-\u200FMax", Hebrew.afterPrefix("\u200FMax"))
        assertEquals("", Hebrew.afterPrefix(""))
    }

    @Test
    fun arrowKeepsRightToLeftOrder() {
        assertEquals("\u200FLeumi\u200F ← HOT", Hebrew.arrow("Leumi", "HOT"))
        assertEquals("Leumi\u200F, Max", Hebrew.list(listOf("Leumi", "Max")))
    }
}
