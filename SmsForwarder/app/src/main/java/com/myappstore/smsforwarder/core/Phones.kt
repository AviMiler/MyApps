package com.myappstore.smsforwarder.core

/**
 * Phone-number helpers tuned for Israeli numbers but safe for any country.
 *
 * Senders come in many shapes: "+972 50-123-4567", "0501234567", "00972501234567",
 * short codes like "8888" and alphanumeric sender ids like "Leumi". Two addresses
 * are considered the same sender when their canonical digits match (or share the
 * last 8 digits), or - for non-phone ids - when their letters/digits match
 * case-insensitively.
 */
object Phones {

    private const val ISRAEL_CC = "972"
    private const val SUFFIX_MATCH = 8

    /** Digits only, with international prefixes rewritten to the local Israeli form. */
    fun canonical(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("00")) digits = digits.substring(2)
        if (digits.startsWith(ISRAEL_CC) && digits.length >= 11) digits = "0" + digits.substring(3)
        return digits
    }

    /** True when [raw] is a dialable phone number rather than a short code or a named sender. */
    fun isPhoneLike(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isLetter() }) return false
        return trimmed.count { it.isDigit() } >= 7
    }

    /** A stable key for de-duplicating addresses. */
    fun key(raw: String): String = if (isPhoneLike(raw)) canonical(raw) else alnum(raw)

    fun same(a: String, b: String): Boolean {
        val phoneA = isPhoneLike(a)
        val phoneB = isPhoneLike(b)
        if (phoneA != phoneB) return false
        if (phoneA) {
            val ca = canonical(a)
            val cb = canonical(b)
            if (ca == cb) return true
            return ca.length >= SUFFIX_MATCH && cb.length >= SUFFIX_MATCH &&
                ca.takeLast(SUFFIX_MATCH) == cb.takeLast(SUFFIX_MATCH)
        }
        val ka = alnum(a)
        return ka.isNotEmpty() && ka == alnum(b)
    }

    /** Human friendly formatting: 050-123-4567, 02-123-4567, otherwise unchanged. */
    fun pretty(raw: String): String {
        val trimmed = raw.trim()
        if (!isPhoneLike(trimmed)) return trimmed
        val c = canonical(trimmed)
        return when {
            c.length == 10 && c.startsWith("0") ->
                "${c.substring(0, 3)}-${c.substring(3, 6)}-${c.substring(6)}"
            c.length == 9 && c.startsWith("0") ->
                "${c.substring(0, 2)}-${c.substring(2, 5)}-${c.substring(5)}"
            else -> trimmed
        }
    }

    /** Whether [query] (as typed in a search box) matches the address. */
    fun matchesQuery(raw: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val qDigits = q.filter { it.isDigit() }
        if (qDigits.length >= 2 && qDigits.length == q.count { it.isLetterOrDigit() }) {
            return raw.filter { it.isDigit() }.contains(qDigits) || canonical(raw).contains(qDigits)
        }
        return raw.contains(q, ignoreCase = true)
    }

    private fun alnum(raw: String) = raw.filter { it.isLetterOrDigit() }.lowercase()
}
