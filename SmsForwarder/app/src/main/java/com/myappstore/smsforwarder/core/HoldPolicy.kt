package com.myappstore.smsforwarder.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class HoldCause { PAUSED, QUIET_HOURS, REST_DAY }

sealed interface HoldDecision {
    /** Forward right away. */
    data object Go : HoldDecision

    /** Keep the message and forward it at [until] (epoch millis). */
    data class Hold(val until: Long, val cause: HoldCause) : HoldDecision

    /** Do not forward the message at all. */
    data class Drop(val cause: HoldCause) : HoldDecision
}

/** Everything that can delay forwarding, already resolved from the user's settings. */
data class HoldRules(
    val pausedUntil: Long = 0L,
    val holdWhilePaused: Boolean = true,
    val quietHours: TimeWindow? = null,
    val holdDuringQuiet: Boolean = true,
    val rest: RestCalendar? = null,
)

object HoldPolicy {

    /** A pause with no end: messages wait until the user resumes. */
    const val FOREVER = Long.MAX_VALUE

    /**
     * Decides what happens to a message that is ready to go at [now].
     * [forceHold] ignores "don't forward" preferences; it is used for messages that
     * were already accepted earlier and must never be silently dropped.
     */
    fun decide(now: Long, zone: ZoneId, rules: HoldRules, forceHold: Boolean = false): HoldDecision {
        val (cause, end) = activeCause(now, zone, rules) ?: return HoldDecision.Go
        if (!forceHold) {
            val drop = when (cause) {
                HoldCause.PAUSED -> !rules.holdWhilePaused
                HoldCause.QUIET_HOURS -> !rules.holdDuringQuiet
                HoldCause.REST_DAY -> false
            }
            if (drop) return HoldDecision.Drop(cause)
        }
        // Chain overlapping holds: a pause that ends during Shabbat keeps holding until Shabbat ends.
        var until = end
        var guard = 0
        while (until != FOREVER && guard++ < MAX_CHAIN) {
            val next = activeCause(until, zone, rules) ?: break
            if (next.second <= until) break
            until = next.second
        }
        return HoldDecision.Hold(until, cause)
    }

    /** The highest-priority hold active at [t] and the moment it ends. */
    fun activeCause(t: Long, zone: ZoneId, rules: HoldRules): Pair<HoldCause, Long>? {
        rules.rest?.activeAt(Instant.ofEpochMilli(t).atZone(zone))?.let { window ->
            return HoldCause.REST_DAY to window.end.toInstant().toEpochMilli()
        }
        if (rules.pausedUntil > t) return HoldCause.PAUSED to rules.pausedUntil
        rules.quietHours?.occurrenceAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(t), zone))?.let { (_, end) ->
            return HoldCause.QUIET_HOURS to end.atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    private const val MAX_CHAIN = 16
}
