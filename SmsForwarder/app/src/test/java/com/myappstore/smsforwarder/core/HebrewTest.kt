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
        assertEquals("‏אבא", Hebrew.afterPrefix("‏אבא"))
        assertEquals("-‏Max", Hebrew.afterPrefix("‏Max"))
        assertEquals("", Hebrew.afterPrefix(""))
    }
}
