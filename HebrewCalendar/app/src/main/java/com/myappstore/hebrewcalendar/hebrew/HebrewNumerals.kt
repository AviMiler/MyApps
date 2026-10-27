package com.myappstore.hebrewcalendar.hebrew

/** Formats numbers as Hebrew letters (gematria), e.g. 15 -> ט״ו, 5787 -> תשפ״ז. */
object HebrewNumerals {

    private val ONES = arrayOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
    private val TENS = arrayOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
    private val HUNDREDS = arrayOf("", "ק", "ר", "ש", "ת")

    /**
     * @param number 1..999 (callers drop the thousands for years, see [formatYear]).
     * @return the letters with geresh (׳) after a single letter or gershayim (״)
     * before the last letter, e.g. format(3) == "ג׳", format(26) == "כ״ו".
     */
    fun format(number: Int): String {
        require(number in 1..999) { "Gematria supports 1..999, got $number" }
        val letters = StringBuilder()
        var hundreds = number / 100
        while (hundreds > 4) {
            letters.append("ת")
            hundreds -= 4
        }
        letters.append(HUNDREDS[hundreds])
        // 15 and 16 are written ט״ו / ט״ז to avoid spelling the divine name.
        when (val rest = number % 100) {
            15 -> letters.append("טו")
            16 -> letters.append("טז")
            else -> letters.append(TENS[rest / 10]).append(ONES[rest % 10])
        }
        return if (letters.length == 1) {
            "$letters׳"
        } else {
            "${letters.dropLast(1)}״${letters.last()}"
        }
    }

    /** @return a Hebrew year without the thousands, e.g. 5787 -> תשפ״ז. */
    fun formatYear(year: Int): String = format(year % 1000)
}
