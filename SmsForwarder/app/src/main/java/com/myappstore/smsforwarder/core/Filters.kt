package com.myappstore.smsforwarder.core

/** Which senders a route listens to. */
object SourceMode {
    /** Only the senders the user picked. */
    const val SELECTED = 0

    /** Any number that is not saved in the phone's contacts. */
    const val UNKNOWN = 1

    /** Every incoming message. */
    const val EVERYONE = 2
}

object SenderMatch {
    fun matches(
        mode: Int,
        sources: List<String>,
        exclusions: List<String>,
        sender: String,
        senderIsContact: Boolean,
    ): Boolean {
        if (exclusions.any { Phones.same(it, sender) }) return false
        return when (mode) {
            SourceMode.SELECTED -> sources.any { Phones.same(it, sender) }
            SourceMode.UNKNOWN -> !senderIsContact
            SourceMode.EVERYONE -> true
            else -> false
        }
    }
}

enum class ContentVerdict { PASS, MISSING_KEYWORD, EXCLUDED_KEYWORD, NOT_A_CODE }

object ContentFilter {
    /**
     * [include] words are alternatives: at least one must appear (when any are set).
     * A single [exclude] word blocks the message. Matching ignores case.
     */
    fun check(
        body: String,
        include: List<String>,
        exclude: List<String>,
        codesOnly: Boolean,
        code: String?,
    ): ContentVerdict {
        val text = body.lowercase()
        val excludes = exclude.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (excludes.any { text.contains(it) }) return ContentVerdict.EXCLUDED_KEYWORD
        val includes = include.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (includes.isNotEmpty() && includes.none { text.contains(it) }) return ContentVerdict.MISSING_KEYWORD
        if (codesOnly && code == null) return ContentVerdict.NOT_A_CODE
        return ContentVerdict.PASS
    }
}
