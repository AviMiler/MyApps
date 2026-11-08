package com.myappstore.smsforwarder.core

import kotlin.math.abs

/** How a message reached the app. */
object Origin {
    /** The SMS broadcast itself. */
    const val SMS = 0

    /**
     * The messaging app's notification: a backup for SMS the phone did not hand over to the app,
     * and the only way to see chat (RCS) messages.
     */
    const val NOTIFICATION = 1
}

/**
 * Pairs up the two ways one message can reach the app - the SMS broadcast and the messaging app's
 * notification - so it is handled once: whichever path claims it first wins and the other copy is
 * dropped. Repeats on the same path are separate messages ("OK" sent twice) and all get through.
 */
class CaptureLog(private val windowMs: Long = WINDOW_MS) {

    private class Capture(val at: Long, val origin: Int, val address: String, val name: String?, val text: String) {
        var paired = false
    }

    private val captures = ArrayDeque<Capture>()

    /**
     * True when the message is new and should be handled; false when the other path already has it.
     * [at] is when the message arrived, so a copy handled late still pairs with its twin.
     */
    @Synchronized
    fun claim(origin: Int, address: String, name: String?, body: String, at: Long): Boolean {
        while (captures.isNotEmpty() && at - captures.first().at > KEEP_MS) captures.removeFirst()
        val text = normalize(body)
        val twin = captures.firstOrNull {
            it.origin != origin && !it.paired && abs(it.at - at) <= windowMs &&
                sameText(it.text, text) && sameSender(it.address, it.name, address, name)
        }
        if (twin != null) {
            twin.paired = true
            return false
        }
        captures.addLast(Capture(at, origin, address, name, text))
        return true
    }

    companion object {
        /** Arrival times of an SMS and its notification copy are seconds apart; minutes apart means two messages. */
        const val WINDOW_MS = 3 * 60_000L
        private const val KEEP_MS = 60 * 60_000L
        private const val INVISIBLE = "\u200B\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069\uFEFF"
        private const val MIN_PREFIX = 12

        /** Text without spacing, direction marks or case, so SMS and notification copies compare equal. */
        fun normalize(text: String): String = text.filterNot { it.isWhitespace() || it in INVISIBLE }.lowercase()

        /** Equal, or one is the other cut short with an ellipsis (long messages in some notifications). */
        private fun sameText(a: String, b: String): Boolean = a == b || isCutFrom(a, b) || isCutFrom(b, a)

        private fun isCutFrom(cut: String, full: String): Boolean {
            val stem = cut.removeSuffix("…").removeSuffix("...")
            return stem.length != cut.length && stem.length >= MIN_PREFIX && full.startsWith(stem)
        }

        /**
         * A notification may show a contact's name instead of the number. When both sides have numbers
         * or both have names they must match; when one side only has a number and the other only a
         * name, the same text at the same moment is taken to be the same message.
         */
        fun sameSender(addressA: String, nameA: String?, addressB: String, nameB: String?): Boolean {
            val phoneA = Phones.isPhoneLike(addressA)
            val phoneB = Phones.isPhoneLike(addressB)
            if (phoneA && phoneB) return Phones.same(addressA, addressB)
            if (nameA != null && nameB != null) return sameName(nameA, nameB)
            if (!phoneA && !phoneB) return Phones.same(addressA, addressB)
            return true
        }

        fun sameName(a: String?, b: String?): Boolean {
            if (a.isNullOrBlank() || b.isNullOrBlank()) return false
            return normalize(a) == normalize(b)
        }
    }
}

/**
 * Keys of notification messages already looked at. Messaging apps re-post a conversation's
 * notification with its earlier messages every time a new one arrives; this keeps each message from
 * being handled again. Saved between runs through [serialize] / [parse].
 */
class SeenKeys(private val maxAgeMs: Long = 2 * 24 * 60 * 60_000L, private val maxSize: Int = 400) {

    private val keys = LinkedHashMap<String, Long>()

    /** True the first time [key] is offered, false afterwards. */
    @Synchronized
    fun firstSighting(key: String, now: Long): Boolean {
        keys.entries.removeAll { now - it.value > maxAgeMs }
        if (keys.containsKey(key)) return false
        keys[key] = now
        while (keys.size > maxSize) keys.remove(keys.keys.first())
        return true
    }

    @Synchronized
    fun serialize(): String = keys.entries.joinToString(RECORD) { "${it.value}$FIELD${it.key}" }

    companion object {
        private const val RECORD = "\u001E"
        private const val FIELD = "\u001F"

        /** A stable key for one message of one conversation. */
        fun keyOf(app: String, sender: String, time: Long, text: String): String =
            listOf(app, CaptureLog.normalize(sender), time.toString(), CaptureLog.normalize(text).hashCode().toString())
                .joinToString("|") { it.replace(RECORD, "").replace(FIELD, "") }

        fun parse(saved: String): SeenKeys {
            val seen = SeenKeys()
            saved.split(RECORD).forEach { record ->
                val at = record.substringBefore(FIELD).toLongOrNull() ?: return@forEach
                seen.keys[record.substringAfter(FIELD)] = at
            }
            return seen
        }
    }
}
