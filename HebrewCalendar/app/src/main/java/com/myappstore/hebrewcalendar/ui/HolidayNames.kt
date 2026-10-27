package com.myappstore.hebrewcalendar.ui

/** Short holiday names that fit a day cell, e.g. "ראש חודש כסלו" -> "ר״ח כסלו". */
object HolidayNames {

    private val ABBREVIATIONS = listOf(
        "ראש חודש " to "ר״ח ",
        "חנוכה – נר " to "חנוכה ",
        "חול המועד " to "חוה״מ "
    )

    fun short(name: String): String =
        ABBREVIATIONS.fold(name) { text, (long, short) -> text.replace(long, short) }
}
