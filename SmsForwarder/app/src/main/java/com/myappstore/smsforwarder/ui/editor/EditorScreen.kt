@file:OptIn(ExperimentalLayoutApi::class)

package com.myappstore.smsforwarder.ui.editor

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.core.ContentFilter
import com.myappstore.smsforwarder.core.ContentVerdict
import com.myappstore.smsforwarder.core.MessageTemplate
import com.myappstore.smsforwarder.core.Otp
import com.myappstore.smsforwarder.core.SmsCounter
import com.myappstore.smsforwarder.core.SourceMode
import com.myappstore.smsforwarder.data.AppSettings
import com.myappstore.smsforwarder.data.Party
import com.myappstore.smsforwarder.data.Route
import com.myappstore.smsforwarder.sms.SimCard
import com.myappstore.smsforwarder.sms.Sims
import com.myappstore.smsforwarder.ui.Fmt
import com.myappstore.smsforwarder.ui.components.ChoiceChips
import com.myappstore.smsforwarder.ui.components.ConfirmDialog
import com.myappstore.smsforwarder.ui.components.DaysPicker
import com.myappstore.smsforwarder.ui.components.HalaaCard
import com.myappstore.smsforwarder.ui.components.InfoCard
import com.myappstore.smsforwarder.ui.components.InlineAction
import com.myappstore.smsforwarder.ui.components.JourneyStep
import com.myappstore.smsforwarder.ui.components.LocalResumeTick
import com.myappstore.smsforwarder.ui.components.PartyChip
import com.myappstore.smsforwarder.ui.components.PrimaryButton
import com.myappstore.smsforwarder.ui.components.RouteDiagram
import com.myappstore.smsforwarder.ui.components.SecondaryButton
import com.myappstore.smsforwarder.ui.components.Segmented
import com.myappstore.smsforwarder.ui.components.SelectChip
import com.myappstore.smsforwarder.ui.components.SmsPreview
import com.myappstore.smsforwarder.ui.components.SwitchRow
import com.myappstore.smsforwarder.ui.components.TimeRangeRow
import com.myappstore.smsforwarder.ui.components.TopBar
import com.myappstore.smsforwarder.ui.components.fieldColors
import com.myappstore.smsforwarder.ui.components.pluralText
import com.myappstore.smsforwarder.ui.theme.Halaa
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(routeId: Long?, onClose: () -> Unit) {
    val editor by produceState<EditorState?>(initialValue = null, routeId) {
        val existing = routeId?.let { Graph.db.routes().byId(it) }
        value = if (existing != null) {
            EditorState(existing)
        } else {
            val count = Graph.db.routes().all().size
            EditorState(Route(colorIndex = count % 8))
        }
    }
    val state = editor
    if (state == null) {
        Box(Modifier.fillMaxSize().background(Halaa.colors.background))
        return
    }
    EditorContent(state, onClose)
}

@Composable
private fun EditorContent(editor: EditorState, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = Halaa.colors
    val scope = rememberCoroutineScope()
    val routeColor = colors.route(editor.colorIndex)
    val settings by Graph.settings.flow.collectAsState()
    val tick = LocalResumeTick.current
    val sims = remember(tick) { Sims.active(context) }

    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var lastPicker by remember { mutableStateOf(PickerTarget.SOURCES) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    fun openPicker(target: PickerTarget) {
        lastPicker = target
        picker = target
    }

    fun close() {
        if (editor.isDirty) confirmDiscard = true else onClose()
    }

    fun save() {
        scope.launch {
            val route = editor.toRoute()
            val dao = Graph.db.routes()
            if (route.id == 0L) dao.insert(route.copy(sortOrder = dao.maxSortOrder() + 1)) else dao.update(route)
            onClose()
        }
    }

    BackHandler(enabled = picker == null) { close() }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = stringResource(if (editor.isNew) R.string.editor_new else R.string.editor_edit),
                onBack = { close() },
            ) {
                if (!editor.isNew) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = colors.danger)
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                EditorHeader(editor, routeColor)
                Spacer(Modifier.height(16.dp))
                FromStep(editor, routeColor, ::openPicker)
                FilterStep(editor, routeColor, sims)
                WhenStep(editor, routeColor)
                ToStep(editor, routeColor, sims) { openPicker(PickerTarget.DESTINATIONS) }
                FormatStep(editor, routeColor)
                MoreStep(editor, routeColor, settings)
                Spacer(Modifier.height(130.dp))
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background, colors.background)))
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.editor_test),
                onClick = { testing = true },
                icon = Icons.Rounded.Science,
                enabled = editor.canSave,
            )
            PrimaryButton(
                text = stringResource(if (editor.canSave) R.string.save else R.string.editor_incomplete),
                onClick = { save() },
                enabled = editor.canSave,
                icon = Icons.Rounded.Check,
                color = routeColor,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(
            visible = picker != null,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            val target = picker ?: lastPicker
            PartyPicker(
                title = stringResource(
                    when (target) {
                        PickerTarget.SOURCES -> R.string.picker_title_sources
                        PickerTarget.EXCLUSIONS -> R.string.picker_title_exclusions
                        PickerTarget.DESTINATIONS -> R.string.picker_title_destinations
                    },
                ),
                initial = editor.parties(target),
                showRecent = target != PickerTarget.DESTINATIONS,
                onDone = { parties ->
                    editor.setParties(target, parties)
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_text),
            confirm = stringResource(R.string.discard_confirm),
            danger = true,
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_route_title),
            text = stringResource(R.string.delete_route_text),
            confirm = stringResource(R.string.delete),
            danger = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    Graph.db.routes().delete(editor.toRoute().id)
                    onClose()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (testing) {
        TestDialog(route = editor.toRoute(), onDismiss = { testing = false })
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun EditorHeader(editor: EditorState, routeColor: Color) {
    val context = LocalContext.current
    val route = editor.toRoute()
    val name = route.displayName(
        stringResource(R.string.source_unknown_short),
        stringResource(R.string.source_everyone_short),
    ).ifBlank { stringResource(R.string.editor_unnamed) }
    HalaaCard(
        Modifier.fillMaxWidth(),
        color = routeColor.copy(alpha = if (Halaa.colors.isDark) 0.14f else 0.08f),
        borderColor = routeColor.copy(alpha = 0.35f),
    ) {
        Text(name, style = MaterialTheme.typography.headlineSmall, color = Halaa.colors.ink, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        RouteDiagram(route, routeColor, active = editor.canSave && editor.enabled)
        Spacer(Modifier.height(12.dp))
        Text(
            routeSentence(context, editor),
            style = MaterialTheme.typography.bodyMedium,
            color = Halaa.colors.inkSoft,
        )
    }
}

/** A plain-Hebrew summary of what the route does. */
private fun routeSentence(context: Context, editor: EditorState): String {
    fun names(list: List<Party>): String {
        val labels = list.map { it.label }
        return when {
            labels.size <= 2 -> labels.joinToString(context.getString(R.string.and_join))
            else -> context.getString(R.string.names_and_more, labels.take(2).joinToString(", "), labels.size - 2)
        }
    }
    val from = when (editor.sourceMode) {
        SourceMode.UNKNOWN -> context.getString(R.string.sentence_from_unknown)
        SourceMode.EVERYONE -> context.getString(R.string.sentence_from_everyone)
        else -> if (editor.sources.isEmpty()) null else context.getString(R.string.sentence_from, names(editor.sources))
    }
    if (from == null) return context.getString(R.string.sentence_pick_senders)
    if (editor.destinations.isEmpty()) return context.getString(R.string.sentence_pick_recipients, from)
    val parts = mutableListOf(context.getString(R.string.sentence_base, from, names(editor.destinations)))
    if (editor.codesOnly) parts += context.getString(R.string.sentence_codes)
    if (editor.includeWords.isNotEmpty()) {
        parts += context.getString(R.string.sentence_words, editor.includeWords.joinToString(", "))
    }
    if (editor.scheduleEnabled) {
        parts += context.getString(
            R.string.sentence_schedule,
            Fmt.days(context, editor.scheduleDays),
            Fmt.minutes(editor.scheduleStart),
            Fmt.minutes(editor.scheduleEnd),
        )
    }
    if (editor.delaySeconds > 0) parts += context.getString(R.string.sentence_delay, delayLabel(context, editor.delaySeconds))
    return parts.joinToString(", ") + "."
}

private fun delayLabel(context: Context, seconds: Int): String = when {
    seconds == 0 -> context.getString(R.string.delay_none)
    seconds < 60 -> context.getString(R.string.delay_seconds, seconds)
    seconds == 60 -> context.getString(R.string.delay_minute)
    else -> context.getString(R.string.delay_minutes, seconds / 60)
}

// ------------------------------------------------------------------ steps

@Composable
private fun FromStep(editor: EditorState, color: Color, openPicker: (PickerTarget) -> Unit) {
    JourneyStep(
        number = 1,
        title = stringResource(R.string.step_from),
        subtitle = stringResource(R.string.step_from_sub),
        color = color,
        isFirst = true,
        isLast = false,
    ) {
        Segmented(
            options = listOf(
                SourceMode.SELECTED to stringResource(R.string.mode_selected),
                SourceMode.UNKNOWN to stringResource(R.string.mode_unknown),
                SourceMode.EVERYONE to stringResource(R.string.mode_everyone),
            ),
            selected = editor.sourceMode,
            onSelect = { editor.sourceMode = it },
        )
        Spacer(Modifier.height(12.dp))
        if (editor.sourceMode == SourceMode.SELECTED) {
            PartiesFlow(editor.sources)
            InlineAction(
                text = stringResource(R.string.add_senders),
                icon = Icons.Rounded.Add,
                onClick = { openPicker(PickerTarget.SOURCES) },
                color = color,
            )
        } else {
            Text(
                stringResource(
                    if (editor.sourceMode == SourceMode.UNKNOWN) R.string.mode_unknown_desc else R.string.mode_everyone_desc,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Halaa.colors.inkSoft,
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.except_label), style = MaterialTheme.typography.labelLarge, color = Halaa.colors.ink)
            Spacer(Modifier.height(8.dp))
            PartiesFlow(editor.exclusions)
            InlineAction(
                text = stringResource(R.string.add_exclusions),
                icon = Icons.Rounded.Add,
                onClick = { openPicker(PickerTarget.EXCLUSIONS) },
                color = color,
            )
        }
    }
}

@Composable
private fun PartiesFlow(parties: SnapshotStateList<Party>) {
    if (parties.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        parties.toList().forEach { party ->
            PartyChip(party, onRemove = { parties.remove(party) })
        }
    }
}

@Composable
private fun FilterStep(editor: EditorState, color: Color, sims: List<SimCard>) {
    val count = editor.filterCount
    JourneyStep(
        number = 2,
        title = stringResource(R.string.step_filter),
        subtitle = if (count == 0) stringResource(R.string.step_filter_none) else pluralText(R.plurals.filters_active, count, count),
        color = color,
        isFirst = false,
        isLast = false,
    ) {
        Text(stringResource(R.string.filter_include), style = MaterialTheme.typography.labelLarge, color = Halaa.colors.ink)
        Spacer(Modifier.height(6.dp))
        WordsInput(editor.includeWords, stringResource(R.string.filter_include_hint), color)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.filter_exclude), style = MaterialTheme.typography.labelLarge, color = Halaa.colors.ink)
        Spacer(Modifier.height(6.dp))
        WordsInput(editor.excludeWords, stringResource(R.string.filter_exclude_hint), Halaa.colors.danger)
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = stringResource(R.string.filter_codes),
            checked = editor.codesOnly,
            onChange = { editor.codesOnly = it },
            description = stringResource(R.string.filter_codes_desc),
            icon = Icons.Rounded.Key,
            color = color,
        )
        if (sims.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.filter_sim), style = MaterialTheme.typography.labelLarge, color = Halaa.colors.ink)
            Spacer(Modifier.height(8.dp))
            ChoiceChips(
                options = listOf(-1 to stringResource(R.string.sim_any)) + sims.map { it.subId to it.label },
                selected = editor.receiveSubId,
                onSelect = { editor.receiveSubId = it },
                color = color,
            )
        }
    }
}

@Composable
private fun WordsInput(words: SnapshotStateList<String>, placeholder: String, color: Color) {
    val colors = Halaa.colors
    var text by remember { mutableStateOf("") }
    fun commit() {
        val word = text.trim().trim(',').trim()
        if (word.isNotEmpty() && words.none { it.equals(word, ignoreCase = true) }) words.add(word)
        text = ""
    }
    if (words.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            words.toList().forEach { word ->
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(color.copy(alpha = if (colors.isDark) 0.22f else 0.12f))
                        .clickable { words.remove(word) }
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(word, style = MaterialTheme.typography.labelLarge, color = color)
                    Spacer(Modifier.size(4.dp))
                    Text("×", style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
    }
    val addIcon: (@Composable () -> Unit)? = if (text.isNotBlank()) {
        {
            IconButton(onClick = { commit() }) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = color)
            }
        }
    } else {
        null
    }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            if (value.endsWith(",") || value.endsWith("\n")) {
                text = value.dropLast(1)
                commit()
            } else {
                text = value
            }
        },
        placeholder = { Text(placeholder, color = colors.inkFaint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        trailingIcon = addIcon,
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WhenStep(editor: EditorState, color: Color) {
    val context = LocalContext.current
    JourneyStep(
        number = 3,
        title = stringResource(R.string.step_when),
        subtitle = if (editor.scheduleEnabled) {
            "${Fmt.days(context, editor.scheduleDays)} · ${Fmt.minutes(editor.scheduleStart)}–${Fmt.minutes(editor.scheduleEnd)}"
        } else {
            stringResource(R.string.step_when_always)
        },
        color = color,
        isFirst = false,
        isLast = false,
    ) {
        SwitchRow(
            title = stringResource(R.string.schedule_switch),
            checked = editor.scheduleEnabled,
            onChange = { editor.scheduleEnabled = it },
            description = stringResource(R.string.schedule_desc),
            icon = Icons.Rounded.Schedule,
            color = color,
        )
        AnimatedVisibility(visible = editor.scheduleEnabled) {
            Column {
                Spacer(Modifier.height(12.dp))
                DaysPicker(editor.scheduleDays, { editor.scheduleDays = it }, color = color)
                Spacer(Modifier.height(12.dp))
                TimeRangeRow(
                    start = editor.scheduleStart,
                    end = editor.scheduleEnd,
                    onStart = { editor.scheduleStart = it },
                    onEnd = { editor.scheduleEnd = it },
                    color = color,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.schedule_global_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Halaa.colors.inkFaint,
        )
    }
}

@Composable
private fun ToStep(editor: EditorState, color: Color, sims: List<SimCard>, onPick: () -> Unit) {
    JourneyStep(
        number = 4,
        title = stringResource(R.string.step_to),
        subtitle = stringResource(R.string.step_to_sub),
        color = color,
        isFirst = false,
        isLast = false,
    ) {
        PartiesFlow(editor.destinations)
        InlineAction(
            text = stringResource(R.string.add_recipients),
            icon = Icons.Rounded.Add,
            onClick = onPick,
            color = color,
        )
        if (sims.size > 1) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.send_sim), style = MaterialTheme.typography.labelLarge, color = Halaa.colors.ink)
            Spacer(Modifier.height(8.dp))
            ChoiceChips(
                options = listOf(-1 to stringResource(R.string.sim_default)) + sims.map { it.subId to it.label },
                selected = editor.sendSubId,
                onSelect = { editor.sendSubId = it },
                color = color,
            )
        }
    }
}

private data class Sample(val address: String, val name: String?, val body: String)

@Composable
private fun sampleFor(editor: EditorState): Sample {
    val first = editor.sources.firstOrNull()
    val wantsCode = editor.codesOnly || editor.template.text.contains(MessageTemplate.CODE)
    val body = stringResource(if (wantsCode) R.string.sample_code_message else R.string.sample_message)
    return if (first != null) {
        Sample(first.address, first.name, body)
    } else {
        Sample("0501234567", stringResource(if (wantsCode) R.string.sample_bank else R.string.sample_name), body)
    }
}

@Composable
private fun FormatStep(editor: EditorState, color: Color) {
    val presets = listOf(
        MessageTemplate.Preset.CLASSIC to stringResource(R.string.preset_classic),
        MessageTemplate.Preset.DETAILED to stringResource(R.string.preset_detailed),
        MessageTemplate.Preset.CODE_FIRST to stringResource(R.string.preset_code_first),
        MessageTemplate.Preset.ONE_LINE to stringResource(R.string.preset_one_line),
        MessageTemplate.Preset.PLAIN to stringResource(R.string.preset_plain),
    )
    JourneyStep(
        number = 5,
        title = stringResource(R.string.step_format),
        subtitle = stringResource(R.string.step_format_sub),
        color = color,
        isFirst = false,
        isLast = false,
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { (preset, label) ->
                SelectChip(
                    label = label,
                    selected = editor.template.text == preset.template,
                    onClick = { editor.applyPreset(preset) },
                    color = color,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = editor.template,
            onValueChange = { editor.template = it },
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.format_insert), style = MaterialTheme.typography.labelMedium, color = Halaa.colors.inkSoft)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MessageTemplate.placeholders.forEach { token ->
                Text(
                    placeholderLabel(token),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
                        .clickable { editor.insertToken(token) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val sample = sampleFor(editor)
        val preview = Graph.engine.preview(editor.toRoute(), sample.address, sample.name, sample.body)
        Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.labelLarge, color = Halaa.colors.ink)
        Spacer(Modifier.height(8.dp))
        SmsPreview(text = preview, fromLabel = stringResource(R.string.preview_from_you))
        Spacer(Modifier.height(8.dp))
        val count = SmsCounter.count(preview)
        Text(
            pluralText(R.plurals.sms_parts, count.parts, count.parts, count.units),
            style = MaterialTheme.typography.labelMedium,
            color = if (count.parts > 2) Halaa.colors.warning else Halaa.colors.inkSoft,
        )
    }
}

@Composable
private fun placeholderLabel(token: String): String = stringResource(
    when (token) {
        MessageTemplate.SENDER -> R.string.token_sender
        MessageTemplate.MESSAGE -> R.string.token_message
        MessageTemplate.CODE -> R.string.token_code
        MessageTemplate.TIME -> R.string.token_time
        MessageTemplate.DATE -> R.string.token_date
        MessageTemplate.NUMBER -> R.string.token_number
        MessageTemplate.NAME -> R.string.token_name
        MessageTemplate.ROUTE -> R.string.token_route
        else -> R.string.token_sim
    },
)

@Composable
private fun MoreStep(editor: EditorState, color: Color, settings: AppSettings) {
    val context = LocalContext.current
    val colors = Halaa.colors
    JourneyStep(
        number = 6,
        title = stringResource(R.string.step_more),
        subtitle = stringResource(R.string.step_more_sub),
        color = color,
        isFirst = false,
        isLast = true,
    ) {
        Text(stringResource(R.string.delay_title), style = MaterialTheme.typography.titleSmall, color = colors.ink)
        Text(stringResource(R.string.delay_desc), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        Spacer(Modifier.height(10.dp))
        ChoiceChips(
            options = listOf(0, 10, 30, 60, 300).map { it to delayLabel(context, it) },
            selected = editor.delaySeconds,
            onSelect = { editor.delaySeconds = it },
            color = color,
        )
        Spacer(Modifier.height(14.dp))
        SwitchRow(
            title = stringResource(R.string.reply_title),
            checked = editor.replyRelay,
            onChange = { editor.replyRelay = it },
            description = stringResource(R.string.reply_desc, settings.replyPrefix),
            icon = Icons.AutoMirrored.Rounded.Reply,
            color = color,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = editor.name,
            onValueChange = { editor.name = it },
            label = { Text(stringResource(R.string.route_name)) },
            placeholder = {
                Text(
                    editor.toRoute().copy(name = "").displayName(
                        stringResource(R.string.source_unknown_short),
                        stringResource(R.string.source_everyone_short),
                    ),
                    color = colors.inkFaint,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.route_color), style = MaterialTheme.typography.labelLarge, color = colors.ink)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.routes.forEachIndexed { index, swatch ->
                val selected = editor.colorIndex == index
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(3.dp, if (selected) colors.ink.copy(alpha = 0.7f) else Color.Transparent, CircleShape)
                        .clickable { editor.colorIndex = index },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SwitchRow(
            title = stringResource(R.string.route_enabled),
            checked = editor.enabled,
            onChange = { editor.enabled = it },
            icon = Icons.Rounded.PowerSettingsNew,
            color = color,
        )
    }
}

// ------------------------------------------------------------------ test dialog

@Composable
private fun TestDialog(route: Route, onDismiss: () -> Unit) {
    val colors = Halaa.colors
    val scope = rememberCoroutineScope()
    val defaultBody = stringResource(if (route.codesOnly) R.string.sample_code_message else R.string.sample_message)
    var body by remember { mutableStateOf(defaultBody) }
    var sending by remember { mutableStateOf(false) }
    var sentCount by remember { mutableStateOf<Int?>(null) }
    val code = Otp.detect(body)
    val verdict = ContentFilter.check(body, route.includeWords, route.excludeWords, route.codesOnly, code)
    val sender = route.sources.firstOrNull()
    val preview = Graph.engine.preview(route, sender?.address ?: "0501234567", sender?.name, body)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.test_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.test_desc), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = {
                        body = it
                        sentCount = null
                    },
                    label = { Text(stringResource(R.string.test_sample)) },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (verdict == ContentVerdict.PASS) {
                    InfoCard(
                        text = stringResource(R.string.test_pass),
                        icon = Icons.Rounded.CheckCircle,
                        color = colors.success,
                    )
                } else {
                    InfoCard(
                        text = stringResource(
                            when (verdict) {
                                ContentVerdict.MISSING_KEYWORD -> R.string.reason_missing_keyword
                                ContentVerdict.EXCLUDED_KEYWORD -> R.string.reason_excluded_keyword
                                else -> R.string.reason_not_code
                            },
                        ) + "\n" + stringResource(R.string.test_blocked_hint),
                        icon = Icons.Rounded.FilterAlt,
                        color = colors.warning,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SmsPreview(text = preview, fromLabel = stringResource(R.string.preview_from_you))
                val sent = sentCount
                if (sent != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        pluralText(R.plurals.test_sent, sent, sent),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.success,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending && route.destinations.isNotEmpty(),
                onClick = {
                    sending = true
                    scope.launch {
                        sentCount = Graph.engine.sendTest(route, body)
                        sending = false
                    }
                },
            ) { Text(stringResource(R.string.test_send_real), color = colors.brand) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = colors.inkSoft) }
        },
    )
}
