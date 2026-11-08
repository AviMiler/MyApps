package com.myappstore.smsforwarder.core

/**
 * Estimates how many SMS segments a text costs, the same way the radio splits it:
 * GSM-7 text fits 160 characters (153 per part when split), anything else - such as
 * Hebrew or emoji - is sent as UCS-2 with 70 characters (67 per part).
 */
object SmsCounter {

    data class Count(val parts: Int, val units: Int, val unicode: Boolean, val perPart: Int)

    private const val GSM_BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val GSM_EXTENDED = "^{}\\[~]|€\u000C"

    fun count(text: String): Count {
        if (text.isEmpty()) return Count(parts = 0, units = 0, unicode = false, perPart = 160)
        var septets = 0
        var gsm = true
        for (ch in text) {
            when {
                GSM_BASIC.indexOf(ch) >= 0 -> septets += 1
                GSM_EXTENDED.indexOf(ch) >= 0 -> septets += 2
                else -> {
                    gsm = false
                    break
                }
            }
        }
        return if (gsm) {
            val parts = if (septets <= 160) 1 else (septets + 152) / 153
            Count(parts, septets, unicode = false, perPart = if (parts == 1) 160 else 153)
        } else {
            val units = text.length
            val parts = if (units <= 70) 1 else (units + 66) / 67
            Count(parts, units, unicode = true, perPart = if (parts == 1) 70 else 67)
        }
    }
}
