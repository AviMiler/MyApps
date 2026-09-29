package com.myappstore.smsforwarder.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TemplateTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val at = LocalDateTime.of(2026, 9, 29, 14, 5).atZone(zone).toInstant().toEpochMilli()

    private fun values(name: String? = "אמא", code: String? = null, message: String = "נתראה בשש") =
        MessageTemplate.Values(
            senderName = name,
            senderAddress = "+972501234567",
            message = message,
            code = code,
            receivedAt = at,
            zone = zone,
            routeName = "משפחה",
            simName = "SIM 1",
        )

    @Test
    fun defaultTemplate() {
        assertEquals("📩 אמא\nנתראה בשש", MessageTemplate.render(MessageTemplate.DEFAULT, values()))
    }

    @Test
    fun unknownSenderFallsBackToPrettyNumber() {
        assertEquals("[050-123-4567] היי", MessageTemplate.render("[{sender}] {message}", values(name = null, message = "היי")))
    }

    @Test
    fun allPlaceholders() {
        val out = MessageTemplate.render("{time} {date} {number} {name} {route} {sim} {unknown}", values())
        assertEquals("14:05 29/09 050-123-4567 אמא משפחה SIM 1 {unknown}", out)
    }

    @Test
    fun codeFirstWithAndWithoutCode() {
        val template = MessageTemplate.Preset.CODE_FIRST.template
        assertEquals(
            "🔑 482913 · Leumi\nקוד: 482913",
            MessageTemplate.render(template, values(name = "Leumi", code = "482913", message = "קוד: 482913")),
        )
        assertEquals("אמא\nנתראה בשש", MessageTemplate.render(template, values()))
    }

    @Test
    fun placeholdersInsideTheMessageAreNotExpanded() {
        assertEquals("📩 אמא\nמה זה {time}?", MessageTemplate.render(MessageTemplate.DEFAULT, values(message = "מה זה {time}?")))
    }

    @Test
    fun digestJoinsMessages() {
        assertEquals("כותרת\n\nא\n\nב", MessageTemplate.digest("כותרת", listOf("א", "ב")))
    }
}
