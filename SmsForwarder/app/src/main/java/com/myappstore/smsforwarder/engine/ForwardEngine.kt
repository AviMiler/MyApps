package com.myappstore.smsforwarder.engine

import android.content.Context
import android.telephony.SmsManager
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.contacts.ContactsRepository
import com.myappstore.smsforwarder.core.ContentFilter
import com.myappstore.smsforwarder.core.ContentVerdict
import com.myappstore.smsforwarder.core.HoldCause
import com.myappstore.smsforwarder.core.HoldDecision
import com.myappstore.smsforwarder.core.HoldPolicy
import com.myappstore.smsforwarder.core.MessageTemplate
import com.myappstore.smsforwarder.core.Otp
import com.myappstore.smsforwarder.core.Phones
import com.myappstore.smsforwarder.core.SenderMatch
import com.myappstore.smsforwarder.core.TimeWindow
import com.myappstore.smsforwarder.data.AppDatabase
import com.myappstore.smsforwarder.data.AppSettings
import com.myappstore.smsforwarder.data.EventKind
import com.myappstore.smsforwarder.data.EventStatus
import com.myappstore.smsforwarder.data.ForwardEvent
import com.myappstore.smsforwarder.data.Reason
import com.myappstore.smsforwarder.data.Route
import com.myappstore.smsforwarder.data.SettingsStore
import com.myappstore.smsforwarder.sms.IncomingSms
import com.myappstore.smsforwarder.sms.Sims
import com.myappstore.smsforwarder.sms.SmsSender
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Decides what happens to every incoming SMS and carries it through to the recipient:
 * matching routes, filters, holds (pause / quiet hours / Shabbat), safety guards,
 * undo windows, sending, delivery tracking, retries and reply relay.
 *
 * All state lives in the database, so the engine can be re-created at any time
 * (for example when the process is started by a broadcast).
 */
class ForwardEngine(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val contacts: ContactsRepository,
    private val notifier: Notifier,
    private val scheduler: Scheduler,
) {

    private val mutex = Mutex()
    private val events get() = db.events()
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val holidays: (LocalDate) -> Boolean = IsraeliHolidays::isYomTov

    // ---------------------------------------------------------------- incoming

    suspend fun onIncoming(sms: IncomingSms) {
        mutex.withLock { process(sms) }
        maintenance()
    }

    private suspend fun process(sms: IncomingSms) {
        val s = settings.value
        val routes = db.routes().enabled()
        if (routes.isEmpty()) return
        val now = sms.receivedAt
        if (handleReply(sms, routes, s, now)) return

        val contact = contacts.lookup(sms.sender)
        val code = Otp.detect(sms.body)
        val hold = HoldPolicy.decide(now, zone, s.holdRules(zone, holidays))
        val localNow = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        val handledRecipients = HashSet<String>()
        // A recipient that one route skips may still be served by a later route, so skips are logged last.
        val skipped = LinkedHashMap<String, ForwardEvent>()
        var scheduled = false

        for (route in routes) {
            val matches = SenderMatch.matches(
                mode = route.sourceMode,
                sources = route.sources.map { it.address },
                exclusions = route.exclusions.map { it.address },
                sender = sms.sender,
                senderIsContact = contact != null,
            )
            if (!matches) continue

            val senderName = contact?.name
                ?: route.sources.firstOrNull { Phones.same(it.address, sms.sender) }?.name
            val routeName = routeName(route)
            val text = render(route, routeName, sms.sender, senderName, sms.body, code, now, sms.subId)
            val routeSkip = skipReason(route, sms, code, localNow, s)

            for (destination in route.destinations) {
                val key = Phones.key(destination.address)
                if (key in handledRecipients) continue
                val base = ForwardEvent(
                    kind = EventKind.FORWARD,
                    routeId = route.id,
                    routeName = routeName,
                    routeColor = route.colorIndex,
                    sender = sms.sender,
                    senderName = senderName,
                    recipient = destination.address,
                    recipientName = destination.name,
                    body = sms.body,
                    outgoing = text,
                    code = code,
                    receivedAt = now,
                    scheduledAt = now,
                    sendSubId = route.sendSubId,
                    receiveSubId = sms.subId,
                )
                val skip = when {
                    routeSkip != Reason.NONE -> routeSkip
                    Phones.same(destination.address, sms.sender) -> Reason.SELF_LOOP
                    hold is HoldDecision.Drop -> reasonFor(hold.cause)
                    s.loopGuard && isDuplicate(sms, destination.address, now) -> Reason.DUPLICATE
                    else -> Reason.NONE
                }
                if (skip != Reason.NONE) {
                    skipped.putIfAbsent(key, base.copy(status = EventStatus.SKIPPED, reason = skip))
                    continue
                }
                handledRecipients += key
                when {
                    s.loopGuard && isBurst(route, now) -> {
                        record(base.copy(status = EventStatus.BLOCKED, reason = Reason.BURST))
                        notifyBurst(routeName, s)
                    }
                    hold is HoldDecision.Hold -> {
                        record(
                            base.copy(
                                status = EventStatus.HELD,
                                reason = reasonFor(hold.cause),
                                scheduledAt = hold.until,
                            ),
                        )
                        scheduled = true
                    }
                    route.delaySeconds > 0 -> {
                        val waiting = base.copy(
                            status = EventStatus.PENDING,
                            reason = Reason.UNDO_WINDOW,
                            scheduledAt = now + route.delaySeconds * 1000L,
                        )
                        val id = record(waiting)
                        notifier.showUndo(waiting.copy(id = id))
                        scheduled = true
                    }
                    else -> sendFresh(base, s)
                }
            }
        }
        skipped.forEach { (key, event) -> if (key !in handledRecipients) record(event) }
        if (scheduled) rearm()
    }

    private fun skipReason(route: Route, sms: IncomingSms, code: String?, localNow: LocalDateTime, s: AppSettings): Int {
        if (!s.masterEnabled) return Reason.APP_OFF
        if (route.receiveSubId >= 0 && sms.subId >= 0 && route.receiveSubId != sms.subId) return Reason.WRONG_SIM
        when (ContentFilter.check(sms.body, route.includeWords, route.excludeWords, route.codesOnly, code)) {
            ContentVerdict.MISSING_KEYWORD -> return Reason.MISSING_KEYWORD
            ContentVerdict.EXCLUDED_KEYWORD -> return Reason.EXCLUDED_KEYWORD
            ContentVerdict.NOT_A_CODE -> return Reason.NOT_A_CODE
            ContentVerdict.PASS -> Unit
        }
        if (route.scheduleEnabled &&
            !TimeWindow(route.scheduleDays, route.scheduleStart, route.scheduleEnd).contains(localNow)
        ) return Reason.OUT_OF_SCHEDULE
        return Reason.NONE
    }

    private suspend fun isDuplicate(sms: IncomingSms, recipient: String, now: Long): Boolean =
        events.countDuplicates(sms.sender, sms.body, recipient, now - DUPLICATE_WINDOW_MS) > 0

    private suspend fun isBurst(route: Route, now: Long): Boolean =
        events.countRouteSince(route.id, now - BURST_WINDOW_MS) >= BURST_LIMIT

    // ---------------------------------------------------------------- reply relay

    /**
     * A recipient of a route with reply relay can answer "#text"; the text is sent back
     * to whoever sent them the most recent forwarded message on that route.
     */
    private suspend fun handleReply(sms: IncomingSms, routes: List<Route>, s: AppSettings, now: Long): Boolean {
        val prefix = s.replyPrefix.trim().ifEmpty { DEFAULT_REPLY_PREFIX }
        val body = sms.body.trimStart()
        if (!body.startsWith(prefix)) return false
        val relayRoutes = routes.filter { route ->
            route.replyRelay && route.destinations.any { Phones.same(it.address, sms.sender) }
        }
        if (relayRoutes.isEmpty()) return false
        val reply = body.removePrefix(prefix).trim()
        if (reply.isEmpty()) return false

        val routeIds = relayRoutes.map { it.id }.toSet()
        val original = events.recentForwards(now - REPLY_WINDOW_MS)
            .firstOrNull { it.routeId in routeIds && Phones.same(it.recipient, sms.sender) }
        val route = relayRoutes.firstOrNull { it.id == original?.routeId } ?: relayRoutes.first()
        val replier = route.destinations.firstOrNull { Phones.same(it.address, sms.sender) }
        val base = ForwardEvent(
            kind = EventKind.REPLY,
            routeId = route.id,
            routeName = routeName(route),
            routeColor = route.colorIndex,
            sender = sms.sender,
            senderName = replier?.name,
            recipient = original?.sender.orEmpty(),
            recipientName = original?.senderName,
            body = sms.body,
            outgoing = reply,
            receivedAt = now,
            scheduledAt = now,
            sendSubId = original?.receiveSubId?.takeIf { it >= 0 } ?: route.sendSubId,
            receiveSubId = sms.subId,
        )
        if (original == null || !Phones.isPhoneLike(original.sender)) {
            record(base.copy(status = EventStatus.SKIPPED, reason = Reason.NO_REPLY_TARGET))
            if (s.notifyProblems) {
                notifier.showProblem(
                    Notifier.ID_REPLY,
                    context.getString(R.string.notif_reply_failed_title),
                    context.getString(R.string.notif_reply_failed_text),
                )
            }
            return true
        }
        if (!s.masterEnabled) {
            record(base.copy(status = EventStatus.SKIPPED, reason = Reason.APP_OFF))
            return true
        }
        // Replies are SMS sent by this phone too, so they respect pauses, quiet hours and Shabbat.
        when (val hold = HoldPolicy.decide(now, zone, s.holdRules(zone, holidays))) {
            is HoldDecision.Hold -> {
                record(base.copy(status = EventStatus.HELD, reason = reasonFor(hold.cause), scheduledAt = hold.until))
                rearm()
            }
            is HoldDecision.Drop -> record(base.copy(status = EventStatus.SKIPPED, reason = reasonFor(hold.cause)))
            HoldDecision.Go -> sendFresh(base, s)
        }
        return true
    }

    // ---------------------------------------------------------------- sending

    /** Records [event] and hands it to the radio; returns its log id. */
    private suspend fun sendFresh(event: ForwardEvent, s: AppSettings): Long {
        if (overDailyLimit(s)) {
            notifyDailyLimit(s)
            return record(event.copy(status = EventStatus.BLOCKED, reason = Reason.DAILY_LIMIT))
        }
        val now = System.currentTimeMillis()
        val sending = event.copy(status = EventStatus.SENDING, attempts = 1, sentAt = now)
        val id = record(sending)
        transmit(listOf(sending.copy(id = id)), sending.outgoing, s)
        return id
    }

    /** Sends waiting events now; several held messages to the same person become one digest. */
    private suspend fun sendGroups(waiting: List<ForwardEvent>, s: AppSettings) {
        for (event in waiting.filter { it.status == EventStatus.PENDING }) {
            sendWaiting(listOf(event), event.outgoing, s)
        }
        val held = waiting.filter { it.status == EventStatus.HELD }
        val groups = held.groupBy { Triple(it.routeId, Phones.key(it.recipient), it.kind) }
        for (group in groups.values) {
            if (s.combineHeld && group.size > 1) {
                for (chunk in group.chunked(DIGEST_MAX)) {
                    if (chunk.size == 1) {
                        sendWaiting(chunk, chunk.first().outgoing, s)
                    } else {
                        val header = context.resources.getQuantityString(R.plurals.digest_header, chunk.size, chunk.size)
                        sendWaiting(chunk, MessageTemplate.digest(header, chunk.map { it.outgoing }), s)
                    }
                }
            } else {
                group.forEach { sendWaiting(listOf(it), it.outgoing, s) }
            }
        }
    }

    private suspend fun sendWaiting(batch: List<ForwardEvent>, text: String, s: AppSettings) {
        batch.forEach { notifier.clearUndo(it.id) }
        if (overDailyLimit(s)) {
            batch.forEach { events.update(it.copy(status = EventStatus.BLOCKED, reason = Reason.DAILY_LIMIT)) }
            notifyDailyLimit(s)
            return
        }
        val now = System.currentTimeMillis()
        val sending = batch.map {
            it.copy(status = EventStatus.SENDING, attempts = it.attempts + 1, sentAt = now, batchSize = batch.size)
        }
        sending.forEach { events.update(it) }
        transmit(sending, text, s)
    }

    private suspend fun transmit(batch: List<ForwardEvent>, text: String, s: AppSettings) {
        val first = batch.first()
        if (!Permissions.canSend(context)) {
            batch.forEach { fail(it, Reason.NO_PERMISSION, null, retryable = false) }
            if (s.notifyProblems) {
                notifier.showProblem(
                    Notifier.ID_NO_PERMISSION,
                    context.getString(R.string.notif_no_permission_title),
                    context.getString(R.string.notif_no_permission_text),
                )
            }
            return
        }
        val subId = Sims.resolveForSending(context, first.sendSubId, s.defaultSubId)
        try {
            SmsSender.send(context, first.recipient, text, subId, batch.map { it.id }.toLongArray())
        } catch (t: Throwable) {
            val detail = t.message ?: t.javaClass.simpleName
            batch.forEach { fail(it, Reason.SEND_ERROR, detail, retryable = true) }
        }
    }

    private suspend fun fail(event: ForwardEvent, reason: Int, detail: String?, retryable: Boolean) {
        if (retryable && event.attempts < MAX_ATTEMPTS) {
            val delay = RETRY_DELAYS_MS[(event.attempts - 1).coerceIn(0, RETRY_DELAYS_MS.size - 1)]
            events.update(
                event.copy(
                    status = EventStatus.PENDING,
                    reason = Reason.RETRY,
                    detail = detail,
                    scheduledAt = System.currentTimeMillis() + delay,
                ),
            )
            rearm()
            return
        }
        val failed = event.copy(status = EventStatus.FAILED, reason = reason, detail = detail)
        events.update(failed)
        if (settings.value.notifyProblems) notifier.showFailure(failed, Texts.reason(context, reason, detail))
    }

    // ---------------------------------------------------------------- radio callbacks

    suspend fun onSent(ids: LongArray, ok: Boolean, errorCode: Int, isLastPart: Boolean) {
        mutex.withLock {
            val batch = events.byIds(ids.toList())
            if (batch.isEmpty()) return@withLock
            if (ok) {
                if (!isLastPart) return@withLock
                events.markSent(ids.toList(), System.currentTimeMillis())
                batch.forEach { notifier.clearFailure(it.id) }
                if (settings.value.notifyEachForward) {
                    val first = batch.first()
                    if (first.kind == EventKind.REPLY) notifier.showReplyRelayed(first) else notifier.showForwarded(first)
                }
            } else {
                val retryable = errorCode in RETRYABLE_ERRORS
                batch.filter { it.status == EventStatus.SENDING }
                    .forEach { fail(it, Reason.SEND_ERROR, errorCode.toString(), retryable) }
            }
        }
    }

    suspend fun onDelivered(ids: LongArray, delivered: Boolean?, isLastPart: Boolean) {
        if (!isLastPart || delivered == null) return
        mutex.withLock {
            if (delivered) {
                events.markDelivered(ids.toList(), System.currentTimeMillis())
            } else {
                for (event in events.byIds(ids.toList())) {
                    val failed = event.copy(status = EventStatus.FAILED, reason = Reason.NOT_DELIVERED)
                    events.update(failed)
                    if (settings.value.notifyProblems) {
                        notifier.showFailure(failed, Texts.reason(context, Reason.NOT_DELIVERED, null))
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- scheduled work

    /** Sends everything whose time has come, unless a hold is (still) active. */
    suspend fun dispatchDue() {
        mutex.withLock {
            val s = settings.value
            if (!s.masterEnabled) {
                scheduler.cancel()
                return@withLock
            }
            val now = System.currentTimeMillis()
            val due = events.due(now)
            if (due.isNotEmpty()) {
                val hold = HoldPolicy.decide(now, zone, s.holdRules(zone, holidays), forceHold = true)
                if (hold is HoldDecision.Hold) {
                    due.forEach {
                        events.update(it.copy(status = EventStatus.HELD, reason = reasonFor(hold.cause), scheduledAt = hold.until))
                        notifier.clearUndo(it.id)
                    }
                } else {
                    sendGroups(due, s)
                }
            }
            rearm()
        }
        maintenance()
    }

    /** Re-computes release times after the user changed pause / quiet hours / Shabbat settings. */
    suspend fun reevaluateHeld() {
        mutex.withLock {
            val s = settings.value
            val now = System.currentTimeMillis()
            val held = events.held()
            if (held.isNotEmpty()) {
                val decision = HoldPolicy.decide(now, zone, s.holdRules(zone, holidays), forceHold = true)
                for (event in held) {
                    val updated = if (decision is HoldDecision.Hold) {
                        event.copy(scheduledAt = decision.until, reason = reasonFor(decision.cause))
                    } else {
                        event.copy(scheduledAt = now)
                    }
                    if (updated != event) events.update(updated)
                }
            }
            rearm()
        }
        dispatchDue()
    }

    /** Sends every waiting message right now, ignoring holds. */
    suspend fun releaseAllNow() {
        mutex.withLock {
            val waiting = events.due(Long.MAX_VALUE)
            if (waiting.isNotEmpty()) sendGroups(waiting, settings.value)
            rearm()
        }
    }

    suspend fun cancelAllWaiting() {
        mutex.withLock {
            events.due(Long.MAX_VALUE).forEach { notifier.clearUndo(it.id) }
            events.cancelAllWaiting()
            rearm()
        }
    }

    suspend fun cancel(id: Long) {
        mutex.withLock {
            val event = events.byId(id) ?: return@withLock
            if (EventStatus.isWaiting(event.status)) {
                events.setStatus(id, EventStatus.CANCELLED, Reason.USER_CANCELLED, null)
            }
            notifier.clearUndo(id)
            rearm()
        }
    }

    suspend fun sendNow(id: Long) {
        mutex.withLock {
            val event = events.byId(id) ?: return@withLock
            if (!EventStatus.isWaiting(event.status)) return@withLock
            sendWaiting(listOf(event), event.outgoing, settings.value)
            rearm()
        }
    }

    /** Sends a failed, blocked, skipped or cancelled message again (forward anyway). */
    suspend fun retry(id: Long) {
        mutex.withLock {
            val event = events.byId(id) ?: return@withLock
            val retryable = event.status == EventStatus.FAILED || event.status == EventStatus.BLOCKED ||
                event.status == EventStatus.SKIPPED || event.status == EventStatus.CANCELLED
            if (!retryable || event.outgoing.isBlank() || event.recipient.isBlank()) return@withLock
            notifier.clearFailure(id)
            sendWaiting(listOf(event.copy(attempts = 0, detail = null)), event.outgoing, settings.value)
        }
    }

    /** Sends [sampleBody] through [route] to its recipients, marked as a test. Returns the log ids to follow. */
    suspend fun sendTest(route: Route, sampleBody: String): List<Long> = mutex.withLock {
        val s = settings.value
        val now = System.currentTimeMillis()
        val routeName = routeName(route)
        val senderName = context.getString(R.string.test_sender_name)
        val code = Otp.detect(sampleBody)
        val text = render(route, routeName, TEST_SENDER, senderName, sampleBody, code, now, -1)
        route.destinations.distinctBy { Phones.key(it.address) }.map { destination ->
            sendFresh(
                ForwardEvent(
                    kind = EventKind.TEST,
                    routeId = route.id,
                    routeName = routeName,
                    routeColor = route.colorIndex,
                    sender = TEST_SENDER,
                    senderName = senderName,
                    recipient = destination.address,
                    recipientName = destination.name,
                    body = sampleBody,
                    outgoing = text,
                    code = code,
                    receivedAt = now,
                    scheduledAt = now,
                    sendSubId = route.sendSubId,
                ),
                s,
            )
        }
    }

    /** Renders [route]'s template for a sample message, for the editor preview. */
    fun preview(route: Route, senderAddress: String, senderName: String?, body: String): String {
        val now = System.currentTimeMillis()
        return render(route, routeName(route), senderAddress, senderName, body, Otp.detect(body), now, -1)
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun record(event: ForwardEvent): Long = events.insert(event)

    private suspend fun rearm() {
        val next = events.nextScheduledAt()
        if (next == null || next == HoldPolicy.FOREVER) scheduler.cancel() else scheduler.scheduleAt(next)
    }

    private suspend fun overDailyLimit(s: AppSettings): Boolean {
        if (s.dailyLimit <= 0) return false
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return events.countSentSince(startOfDay) >= s.dailyLimit
    }

    private fun notifyDailyLimit(s: AppSettings) {
        if (!s.notifyProblems) return
        notifier.showProblem(
            Notifier.ID_DAILY_LIMIT,
            context.getString(R.string.notif_limit_title),
            context.getString(R.string.notif_limit_text, s.dailyLimit),
        )
    }

    private fun notifyBurst(routeName: String, s: AppSettings) {
        if (!s.notifyProblems) return
        notifier.showProblem(
            Notifier.ID_BURST,
            context.getString(R.string.notif_burst_title),
            context.getString(R.string.notif_burst_text, routeName),
        )
    }

    private fun routeName(route: Route): String = route.displayName(
        context.getString(R.string.source_unknown_short),
        context.getString(R.string.source_everyone_short),
    )

    private fun render(
        route: Route,
        routeName: String,
        senderAddress: String,
        senderName: String?,
        body: String,
        code: String?,
        receivedAt: Long,
        subId: Int,
    ): String {
        val template = route.template.ifBlank { MessageTemplate.DEFAULT }
        val simName = if (template.contains(MessageTemplate.SIM)) Sims.nameOf(context, subId) else null
        return MessageTemplate.render(
            template,
            MessageTemplate.Values(
                senderName = senderName,
                senderAddress = senderAddress,
                message = body,
                code = code,
                receivedAt = receivedAt,
                zone = zone,
                routeName = routeName,
                simName = simName,
            ),
        )
    }

    private fun reasonFor(cause: HoldCause): Int = when (cause) {
        HoldCause.PAUSED -> Reason.PAUSED
        HoldCause.QUIET_HOURS -> Reason.QUIET_HOURS
        HoldCause.REST_DAY -> Reason.REST_DAY
    }

    private suspend fun maintenance() {
        val s = settings.value
        val now = System.currentTimeMillis()
        if (now - s.lastCleanupAt < DAY_MS) return
        settings.update { it.copy(lastCleanupAt = now) }
        if (s.retentionDays > 0) events.deleteOlderThan(now - s.retentionDays * DAY_MS)
    }

    companion object {
        const val TEST_SENDER = "Halaa"
        const val DEFAULT_REPLY_PREFIX = "#"
        private const val MAX_ATTEMPTS = 3
        private val RETRY_DELAYS_MS = longArrayOf(60_000L, 5 * 60_000L, 20 * 60_000L)
        private const val DUPLICATE_WINDOW_MS = 2 * 60_000L
        private const val BURST_WINDOW_MS = 10 * 60_000L
        private const val BURST_LIMIT = 15
        private const val REPLY_WINDOW_MS = 7 * 24 * 60 * 60_000L
        private const val DIGEST_MAX = 8
        private const val DAY_MS = 24 * 60 * 60_000L
        private val RETRYABLE_ERRORS = setOf(
            SmsManager.RESULT_ERROR_GENERIC_FAILURE,
            SmsManager.RESULT_ERROR_RADIO_OFF,
            SmsManager.RESULT_ERROR_NULL_PDU,
            SmsManager.RESULT_ERROR_NO_SERVICE,
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED,
            SmsManager.RESULT_RADIO_NOT_AVAILABLE,
        )
    }
}
