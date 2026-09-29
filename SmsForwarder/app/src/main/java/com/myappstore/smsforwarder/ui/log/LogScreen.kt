@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.myappstore.smsforwarder.ui.log

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.core.Phones
import com.myappstore.smsforwarder.data.EventKind
import com.myappstore.smsforwarder.data.EventStatus
import com.myappstore.smsforwarder.data.ForwardEvent
import com.myappstore.smsforwarder.data.Reason
import com.myappstore.smsforwarder.engine.Texts
import com.myappstore.smsforwarder.ui.Fmt
import com.myappstore.smsforwarder.ui.components.ConfirmDialog
import com.myappstore.smsforwarder.ui.components.EmptyState
import com.myappstore.smsforwarder.ui.components.HalaaCard
import com.myappstore.smsforwarder.ui.components.InlineAction
import com.myappstore.smsforwarder.ui.components.LoadingBox
import com.myappstore.smsforwarder.ui.components.Pill
import com.myappstore.smsforwarder.ui.components.RouteIllustration
import com.myappstore.smsforwarder.ui.components.SectionHeader
import com.myappstore.smsforwarder.ui.components.SelectChip
import com.myappstore.smsforwarder.ui.components.fieldColors
import com.myappstore.smsforwarder.ui.components.rememberNow
import com.myappstore.smsforwarder.ui.theme.Halaa
import com.myappstore.smsforwarder.ui.theme.Secular
import kotlinx.coroutines.launch

private enum class LogFilter { ALL, FORWARDED, WAITING, PROBLEMS, SKIPPED }

private fun LogFilter.matches(event: ForwardEvent): Boolean = when (this) {
    LogFilter.ALL -> true
    LogFilter.FORWARDED -> event.status == EventStatus.SENDING || EventStatus.isSuccess(event.status)
    LogFilter.WAITING -> EventStatus.isWaiting(event.status)
    LogFilter.PROBLEMS -> event.status == EventStatus.FAILED || event.status == EventStatus.BLOCKED
    LogFilter.SKIPPED -> event.status == EventStatus.SKIPPED || event.status == EventStatus.CANCELLED
}

private data class StatusStyle(val label: Int, val icon: ImageVector, val color: Color, val background: Color)

@Composable
private fun statusStyle(status: Int): StatusStyle {
    val c = Halaa.colors
    return when (status) {
        EventStatus.PENDING -> StatusStyle(R.string.status_pending, Icons.Rounded.Schedule, c.held, c.heldSoft)
        EventStatus.HELD -> StatusStyle(R.string.status_held, Icons.Rounded.HourglassTop, c.held, c.heldSoft)
        EventStatus.SENDING -> StatusStyle(R.string.status_sending, Icons.AutoMirrored.Rounded.Send, c.brand, c.brandSoft)
        EventStatus.SENT -> StatusStyle(R.string.status_sent, Icons.Rounded.Done, c.success, c.successSoft)
        EventStatus.DELIVERED -> StatusStyle(R.string.status_delivered, Icons.Rounded.DoneAll, c.success, c.successSoft)
        EventStatus.FAILED -> StatusStyle(R.string.status_failed, Icons.Rounded.ErrorOutline, c.danger, c.dangerSoft)
        EventStatus.BLOCKED -> StatusStyle(R.string.status_blocked, Icons.Rounded.Shield, c.warning, c.warningSoft)
        EventStatus.CANCELLED -> StatusStyle(R.string.status_cancelled, Icons.Rounded.Close, c.inkSoft, c.sunken)
        else -> StatusStyle(R.string.status_skipped, Icons.Rounded.RemoveCircleOutline, c.inkSoft, c.sunken)
    }
}

@Composable
fun LogScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val colors = Halaa.colors
    val scope = rememberCoroutineScope()
    val events by remember { Graph.db.events().observeRecent(800) }.collectAsState(initial = null)
    val settings by Graph.settings.flow.collectAsState()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val now = rememberNow()
    val clipboard = LocalClipboardManager.current

    val all = events
    val filtered = remember(all, filter, query) {
        val q = query.trim()
        all.orEmpty().filter { event ->
            filter.matches(event) && (
                q.isEmpty() ||
                    event.body.contains(q, ignoreCase = true) ||
                    event.senderLabel.contains(q, ignoreCase = true) ||
                    event.recipientLabel.contains(q, ignoreCase = true) ||
                    Phones.matchesQuery(event.sender, q) ||
                    event.routeName.contains(q, ignoreCase = true)
                )
        }
    }
    val today = Fmt.zoned(now).toLocalDate()
    val groups = remember(filtered, today) { filtered.groupBy { Fmt.zoned(it.receivedAt).toLocalDate() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "title") {
            SectionHeader(title = stringResource(R.string.log_title), modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) query = ""
                }) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.log_search), tint = colors.inkSoft)
                }
                IconButton(onClick = { confirmClear = true }, enabled = !all.isNullOrEmpty()) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.log_clear), tint = colors.inkSoft)
                }
            }
        }
        item(key = "search") {
            AnimatedVisibility(visible = searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.log_search_hint), color = colors.inkFaint) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.inkSoft) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )
            }
        }
        item(key = "filters") {
            val counts = remember(all) { LogFilter.entries.associateWith { f -> all.orEmpty().count { f.matches(it) } } }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogFilter.entries.forEach { f ->
                    val label = stringResource(
                        when (f) {
                            LogFilter.ALL -> R.string.filter_all
                            LogFilter.FORWARDED -> R.string.filter_forwarded
                            LogFilter.WAITING -> R.string.filter_waiting
                            LogFilter.PROBLEMS -> R.string.filter_problems
                            LogFilter.SKIPPED -> R.string.filter_skipped
                        },
                    )
                    val count = counts[f] ?: 0
                    SelectChip(
                        label = if (f == LogFilter.ALL || count == 0) label else "$label · $count",
                        selected = filter == f,
                        onClick = { filter = f },
                        color = colors.ink,
                    )
                }
            }
        }
        when {
            all == null -> item(key = "loading") { LoadingBox() }
            filtered.isEmpty() -> item(key = "empty") {
                EmptyState(
                    title = stringResource(if (all.isEmpty()) R.string.log_empty_title else R.string.log_no_match_title),
                    body = stringResource(if (all.isEmpty()) R.string.log_empty_body else R.string.log_no_match_body),
                    illustration = {
                        if (all.isEmpty()) {
                            RouteIllustration(
                                Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                animate = false,
                            )
                        }
                    },
                )
            }
            else -> groups.forEach { (date, dayEvents) ->
                stickyHeader(key = "day-$date") {
                    Text(
                        Fmt.dayLabel(context, date, today),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.inkSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.background)
                            .padding(top = 14.dp, bottom = 6.dp),
                    )
                }
                items(dayEvents, key = { it.id }) { event ->
                    EventRow(
                        event = event,
                        expanded = expanded == event.id,
                        hideBody = settings.hideBodies && expanded != event.id,
                        isLast = event == dayEvents.last(),
                        onToggle = { expanded = if (expanded == event.id) null else event.id },
                        onSendNow = { scope.launch { Graph.engine.sendNow(event.id) } },
                        onCancel = { scope.launch { Graph.engine.cancel(event.id) } },
                        onRetry = { scope.launch { Graph.engine.retry(event.id) } },
                        onDelete = { scope.launch { Graph.db.events().delete(event.id) } },
                        onCopyCode = { code ->
                            clipboard.setText(AnnotatedString(code))
                            Toast.makeText(context, context.getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.log_clear_title),
            text = stringResource(R.string.log_clear_text),
            confirm = stringResource(R.string.log_clear),
            danger = true,
            onConfirm = {
                confirmClear = false
                scope.launch { Graph.db.events().clearFinished() }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun EventRow(
    event: ForwardEvent,
    expanded: Boolean,
    hideBody: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onSendNow: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onCopyCode: (String) -> Unit,
) {
    val colors = Halaa.colors
    val context = LocalContext.current
    val style = statusStyle(event.status)
    val routeColor = colors.route(event.routeColor)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val railColor = colors.outline

    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 62.dp.toPx().let { if (rtl) size.width - it else it }
                drawLine(
                    railColor,
                    Offset(x, 0f),
                    Offset(x, if (isLast) 26.dp.toPx() else size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            },
    ) {
        Text(
            Fmt.time(event.receivedAt),
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkSoft,
            modifier = Modifier
                .width(50.dp)
                .padding(top = 18.dp),
        )
        Box(Modifier.width(24.dp).padding(top = 19.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(colors.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(routeColor),
            )
        }
        HalaaCard(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 5.dp)
                .animateContentSize(),
            onClick = onToggle,
            contentPadding = 14.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.kind == EventKind.REPLY) {
                    Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null, tint = routeColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                } else if (event.kind == EventKind.TEST) {
                    Icon(Icons.Rounded.Science, contentDescription = null, tint = routeColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    "‏${event.senderLabel} ← ${event.recipientLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Pill(stringResource(style.label), style.color, style.background, icon = style.icon)
            }
            Spacer(Modifier.height(6.dp))
            if (hideBody) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.log_hidden_body), style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
                }
            } else {
                Text(
                    event.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val code = event.code
            if (code != null && !hideBody) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.brandSoft)
                        .clickable { onCopyCode(code) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = colors.brand, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        code,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = Secular, letterSpacing = 2.sp),
                        color = colors.brand,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.copy_code), tint = colors.brand, modifier = Modifier.size(16.dp))
                }
            }
            if (event.reason != Reason.NONE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    Texts.reason(context, event.reason, event.detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = style.color,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Details(event)
            }
            val actions = actionsFor(event, expanded)
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    actions.forEach { action ->
                        when (action) {
                            RowAction.SEND_NOW -> InlineAction(stringResource(R.string.action_send_now), Icons.AutoMirrored.Rounded.Send, onSendNow, color = colors.brand)
                            RowAction.CANCEL -> InlineAction(stringResource(R.string.action_cancel), Icons.Rounded.Close, onCancel, color = colors.danger)
                            RowAction.RETRY -> InlineAction(stringResource(R.string.action_retry), Icons.Rounded.Refresh, onRetry, color = colors.brand)
                            RowAction.FORWARD_ANYWAY -> InlineAction(stringResource(R.string.action_forward_anyway), Icons.AutoMirrored.Rounded.Send, onRetry, color = colors.ink)
                            RowAction.DELETE -> InlineAction(stringResource(R.string.delete), Icons.Rounded.DeleteOutline, onDelete, color = colors.inkSoft)
                        }
                    }
                }
            }
        }
    }
}

private enum class RowAction { SEND_NOW, CANCEL, RETRY, FORWARD_ANYWAY, DELETE }

private fun actionsFor(event: ForwardEvent, expanded: Boolean): List<RowAction> {
    val actions = mutableListOf<RowAction>()
    val sendable = event.outgoing.isNotBlank() && event.recipient.isNotBlank()
    when (event.status) {
        EventStatus.PENDING, EventStatus.HELD -> {
            actions += RowAction.SEND_NOW
            actions += RowAction.CANCEL
        }
        EventStatus.FAILED, EventStatus.BLOCKED -> if (sendable) actions += RowAction.RETRY
        EventStatus.SKIPPED, EventStatus.CANCELLED -> if (expanded && sendable) actions += RowAction.FORWARD_ANYWAY
    }
    if (expanded && !EventStatus.isWaiting(event.status) && event.status != EventStatus.SENDING) actions += RowAction.DELETE
    return actions
}

@Composable
private fun Details(event: ForwardEvent) {
    val colors = Halaa.colors
    val context = LocalContext.current
    val now = rememberNow()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunken)
            .padding(12.dp),
    ) {
        if (event.outgoing.isNotBlank()) {
            Text(stringResource(R.string.log_sent_text), style = MaterialTheme.typography.labelMedium, color = colors.inkSoft)
            Spacer(Modifier.height(4.dp))
            Text(event.outgoing, style = MaterialTheme.typography.bodySmall, color = colors.ink)
            Spacer(Modifier.height(10.dp))
        }
        DetailLine(stringResource(R.string.log_route), event.routeName.ifBlank { "—" })
        DetailLine(stringResource(R.string.log_from), "${event.senderLabel} (${Phones.pretty(event.sender)})")
        DetailLine(stringResource(R.string.log_to), "${event.recipientLabel} (${Phones.pretty(event.recipient)})")
        DetailLine(stringResource(R.string.log_received), Fmt.at(context, event.receivedAt, now))
        if (EventStatus.isWaiting(event.status)) {
            DetailLine(stringResource(R.string.log_scheduled), Fmt.until(context, event.scheduledAt, now))
        }
        if (event.sentAt > 0) DetailLine(stringResource(R.string.log_sent_at), Fmt.at(context, event.sentAt, now))
        if (event.deliveredAt > 0) DetailLine(stringResource(R.string.log_delivered_at), Fmt.at(context, event.deliveredAt, now))
        if (event.attempts > 1) DetailLine(stringResource(R.string.log_attempts), event.attempts.toString())
        if (event.batchSize > 1) DetailLine(stringResource(R.string.log_batch), event.batchSize.toString())
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Halaa.colors.inkSoft, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = Halaa.colors.ink, modifier = Modifier.weight(1f))
    }
}

