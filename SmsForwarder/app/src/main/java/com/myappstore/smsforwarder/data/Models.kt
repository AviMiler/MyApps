package com.myappstore.smsforwarder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myappstore.smsforwarder.core.Days
import com.myappstore.smsforwarder.core.Hebrew
import com.myappstore.smsforwarder.core.MessageTemplate
import com.myappstore.smsforwarder.core.Phones
import com.myappstore.smsforwarder.core.SourceMode

/** A phone number or sender id, with the contact details we knew when it was picked. */
data class Party(
    val address: String,
    val name: String? = null,
    val contactId: Long = 0L,
    val photoUri: String? = null,
) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: Phones.pretty(address)
}

/**
 * A forwarding route: messages from [sources] (or from unknown numbers / everyone,
 * depending on [sourceMode]) that pass the filters are sent on to [destinations].
 */
@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String = "",
    val colorIndex: Int = 0,
    val enabled: Boolean = true,
    val sourceMode: Int = SourceMode.SELECTED,
    val sources: List<Party> = emptyList(),
    val exclusions: List<Party> = emptyList(),
    val destinations: List<Party> = emptyList(),
    val includeWords: List<String> = emptyList(),
    val excludeWords: List<String> = emptyList(),
    val codesOnly: Boolean = false,
    val scheduleEnabled: Boolean = false,
    val scheduleDays: Int = Days.SUN_TO_THU,
    val scheduleStart: Int = 8 * 60,
    val scheduleEnd: Int = 18 * 60,
    val template: String = MessageTemplate.DEFAULT,
    val sendSubId: Int = -1,
    val receiveSubId: Int = -1,
    val delaySeconds: Int = 0,
    val replyRelay: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
) {
    /** Explicit name, or "first sender ← first recipient". */
    fun displayName(unknownLabel: String, everyoneLabel: String): String {
        if (name.isNotBlank()) return name
        val from = when (sourceMode) {
            SourceMode.UNKNOWN -> unknownLabel
            SourceMode.EVERYONE -> everyoneLabel
            else -> sources.firstOrNull()?.label.orEmpty()
        }
        val to = destinations.firstOrNull()?.label.orEmpty()
        return when {
            from.isEmpty() -> to
            to.isEmpty() -> from
            else -> Hebrew.arrow(from, to)
        }
    }
}

object EventKind {
    const val FORWARD = 0
    const val REPLY = 1
    const val TEST = 2
}

object EventStatus {
    /** Waiting for its scheduled time (undo window or retry). */
    const val PENDING = 0

    /** Held by a pause, quiet hours or Shabbat, released at [ForwardEvent.scheduledAt]. */
    const val HELD = 1
    const val SENDING = 2
    const val SENT = 3
    const val DELIVERED = 4
    const val FAILED = 5

    /** Not forwarded by design (filters, schedule, app off...). */
    const val SKIPPED = 6
    const val CANCELLED = 7

    /** Stopped by a safety guard (daily limit, loop). */
    const val BLOCKED = 8

    fun isWaiting(status: Int) = status == PENDING || status == HELD
    fun isSuccess(status: Int) = status == SENT || status == DELIVERED
}

object Reason {
    const val NONE = 0
    const val APP_OFF = 1
    const val PAUSED = 2
    const val QUIET_HOURS = 3
    const val REST_DAY = 4
    const val OUT_OF_SCHEDULE = 5
    const val MISSING_KEYWORD = 6
    const val EXCLUDED_KEYWORD = 7
    const val NOT_A_CODE = 8
    const val WRONG_SIM = 9
    const val SELF_LOOP = 10
    const val DUPLICATE = 11
    const val BURST = 12
    const val DAILY_LIMIT = 13
    const val NO_PERMISSION = 14
    const val SEND_ERROR = 15
    const val NOT_DELIVERED = 16
    const val USER_CANCELLED = 17
    const val UNDO_WINDOW = 18
    const val RETRY = 19
    const val NO_REPLY_TARGET = 20
}

/** One forwarding attempt of one message to one recipient - the unit shown in the log. */
@Entity(
    tableName = "events",
    indices = [Index("status"), Index("receivedAt"), Index("routeId")],
)
data class ForwardEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val kind: Int = EventKind.FORWARD,
    val routeId: Long = 0L,
    val routeName: String = "",
    val routeColor: Int = 0,
    val sender: String = "",
    val senderName: String? = null,
    val recipient: String = "",
    val recipientName: String? = null,
    val body: String = "",
    val outgoing: String = "",
    val code: String? = null,
    val receivedAt: Long = 0L,
    val scheduledAt: Long = 0L,
    val status: Int = EventStatus.PENDING,
    val reason: Int = Reason.NONE,
    val detail: String? = null,
    val attempts: Int = 0,
    val sendSubId: Int = -1,
    val receiveSubId: Int = -1,
    val sentAt: Long = 0L,
    val deliveredAt: Long = 0L,
    val batchSize: Int = 1,
) {
    val senderLabel: String get() = senderName?.takeIf { it.isNotBlank() } ?: Phones.pretty(sender)
    val recipientLabel: String get() = recipientName?.takeIf { it.isNotBlank() } ?: Phones.pretty(recipient)
}

/** Aggregated counts per route for the home screen. */
data class RouteStat(
    val routeId: Long,
    val count: Int,
    val lastAt: Long,
)
