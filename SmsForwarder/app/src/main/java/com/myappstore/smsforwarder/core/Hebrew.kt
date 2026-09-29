package com.myappstore.smsforwarder.core

/** Small rules of Hebrew typography. */
object Hebrew {

    /**
     * A one-letter prefix (ו, ל, מ, ב…) joins a Hebrew word directly, but takes a hyphen before digits
     * and foreign letters: "לאבא", yet "ל-050…" and "מ-Leumi". Returns [word] ready to follow the prefix.
     */
    fun afterPrefix(word: String): String {
        val first = word.firstOrNull { it.isLetterOrDigit() || it == '+' } ?: return word
        return if (first in '\u05D0'..'\u05EA') word else "-$word"
    }

    /**
     * "from ← to", reading right-to-left even when both names are Latin or numbers: without the
     * marks, "Leumi ← HOT" would form one left-to-right run and the arrow would point backwards.
     */
    fun arrow(from: String, to: String): String = "$RLM$from$RLM ← $to"

    /** Joins names with commas so that consecutive Latin names keep their order in right-to-left text. */
    fun list(names: List<String>): String = names.joinToString("$RLM, ")

    private const val RLM = '\u200F'
}
