package com.myappstore.smsforwarder.core

/** Small rules of Hebrew typography. */
object Hebrew {

    /**
     * A one-letter prefix (ו, ל, מ, ב…) joins a Hebrew word directly, but takes a hyphen before digits
     * and foreign letters: "לאבא", yet "ל-050…" and "מ-Leumi". Returns [word] ready to follow the prefix.
     */
    fun afterPrefix(word: String): String {
        val first = word.firstOrNull { it.isLetterOrDigit() || it == '+' } ?: return word
        return if (first in 'א'..'ת') word else "-$word"
    }
}
