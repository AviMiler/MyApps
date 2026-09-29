package com.myappstore.smsforwarder.core

/**
 * Finds one-time passwords / verification codes in an SMS body.
 *
 * A message only counts as a code message when it mentions a code-related word
 * (Hebrew or English); the chosen code is the 4-8 digit number closest to that
 * word, skipping money amounts, dates, times and long phone numbers.
 */
object Otp {

    /** Hebrew words are matched as substrings because prefixes attach to them (והקוד, לסיסמה). */
    private val hebrewKeywords = listOf(
        "קוד", "סיסמ", "אימות", "הזדהות", "חד פעמי", "חד-פעמי", "חד פעמית",
    )

    /** English words are matched on word boundaries so "pin" does not hit "shipping". */
    private val englishKeywords = Regex(
        """\b(code|otp|passcode|password|pin|verification|verify|one[- ]time|login|log in|sign[- ]in|2fa|authenticat\w*)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val candidate = Regex("""(?<!\d)(\d{3}[- ]\d{3}|\d{4,8})(?!\d)""")

    /** Words that introduce numbers which are not codes (card endings, account numbers). */
    private val notCodeContext = listOf("כרטיס", "חשבון", "מסתיים", "card", "ending", "account", "acct")

    private const val CURRENCY = "₪$€£"
    private const val CODE_BEFORE_WORD_PENALTY = 6
    private const val LONG_CODE_BONUS = 2
    private const val CONTEXT_PENALTY = 40

    fun detect(text: String): String? {
        if (text.isBlank()) return null
        val keywords = keywordRanges(text)
        if (keywords.isEmpty()) return null

        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (match in candidate.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (isAmountDateOrTime(text, start, end)) continue
            val digits = match.value.filter { it.isDigit() }
            // Codes usually follow the word ("code: 1234"), longer codes are likelier, and
            // numbers right after "card"/"account" are almost never codes.
            var score = keywords.minOf { range ->
                distance(range, start, end) + if (range.first >= end) CODE_BEFORE_WORD_PENALTY else 0
            }
            if (digits.length >= 6) score -= LONG_CODE_BONUS
            val lead = text.substring(maxOf(0, start - 14), start).lowercase()
            if (notCodeContext.any { lead.contains(it) }) score += CONTEXT_PENALTY
            if (score < bestScore) {
                bestScore = score
                best = digits
            }
        }
        return best
    }

    private fun keywordRanges(text: String): List<IntRange> {
        val lower = text.lowercase()
        val ranges = mutableListOf<IntRange>()
        for (word in hebrewKeywords) {
            var index = lower.indexOf(word)
            while (index >= 0) {
                ranges += index until index + word.length
                index = lower.indexOf(word, index + word.length)
            }
        }
        englishKeywords.findAll(text).forEach { ranges += it.range }
        return ranges
    }

    private fun distance(keyword: IntRange, start: Int, end: Int): Int = when {
        keyword.last < start -> start - keyword.last
        keyword.first >= end -> keyword.first - end + 1
        else -> 0
    }

    private fun isAmountDateOrTime(text: String, start: Int, end: Int): Boolean {
        val before = text.substring(maxOf(0, start - 2), start)
        if (before.any { it in CURRENCY }) return true
        val after = text.substring(end, minOf(text.length, end + 6)).trimStart()
        if (after.startsWith("₪") || after.startsWith("%") || after.startsWith("ש\"ח") ||
            after.startsWith("ש״ח") || after.startsWith("שקל") || after.startsWith("ש'")
        ) return true
        if (start >= 2 && text[start - 1] in "/.:" && text[start - 2].isDigit()) return true
        if (end + 1 < text.length && text[end] in "/.:" && text[end + 1].isDigit()) return true
        return false
    }
}
