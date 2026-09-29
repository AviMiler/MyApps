package com.myappstore.smsforwarder.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.myappstore.smsforwarder.core.MessageTemplate
import com.myappstore.smsforwarder.core.Phones
import com.myappstore.smsforwarder.core.SourceMode
import com.myappstore.smsforwarder.data.Party
import com.myappstore.smsforwarder.data.Route

/** Editable copy of a [Route] held in Compose state while the editor is open. */
class EditorState(private val original: Route) {

    val isNew: Boolean get() = original.id == 0L

    var name by mutableStateOf(original.name)
    var colorIndex by mutableIntStateOf(original.colorIndex)
    var enabled by mutableStateOf(original.enabled)
    var sourceMode by mutableIntStateOf(original.sourceMode)
    val sources = mutableStateListOf<Party>().apply { addAll(original.sources) }
    val exclusions = mutableStateListOf<Party>().apply { addAll(original.exclusions) }
    val destinations = mutableStateListOf<Party>().apply { addAll(original.destinations) }
    val includeWords = mutableStateListOf<String>().apply { addAll(original.includeWords) }
    val excludeWords = mutableStateListOf<String>().apply { addAll(original.excludeWords) }
    var codesOnly by mutableStateOf(original.codesOnly)
    var scheduleEnabled by mutableStateOf(original.scheduleEnabled)
    var scheduleDays by mutableIntStateOf(original.scheduleDays)
    var scheduleStart by mutableIntStateOf(original.scheduleStart)
    var scheduleEnd by mutableIntStateOf(original.scheduleEnd)
    var template by mutableStateOf(TextFieldValue(original.template))
    var sendSubId by mutableIntStateOf(original.sendSubId)
    var receiveSubId by mutableIntStateOf(original.receiveSubId)
    var delaySeconds by mutableIntStateOf(original.delaySeconds)
    var replyRelay by mutableStateOf(original.replyRelay)

    val hasSenders: Boolean get() = sourceMode != SourceMode.SELECTED || sources.isNotEmpty()
    val canSave: Boolean get() = hasSenders && destinations.isNotEmpty()
    val isDirty: Boolean get() = toRoute().copy(createdAt = 0L) != original.copy(createdAt = 0L)

    val filterCount: Int
        get() = (if (includeWords.isNotEmpty()) 1 else 0) +
            (if (excludeWords.isNotEmpty()) 1 else 0) +
            (if (codesOnly) 1 else 0) +
            (if (receiveSubId >= 0) 1 else 0)

    fun toRoute(): Route = original.copy(
        name = name.trim(),
        colorIndex = colorIndex,
        enabled = enabled,
        sourceMode = sourceMode,
        sources = sources.toList(),
        exclusions = exclusions.toList(),
        destinations = destinations.toList(),
        includeWords = includeWords.toList(),
        excludeWords = excludeWords.toList(),
        codesOnly = codesOnly,
        scheduleEnabled = scheduleEnabled,
        scheduleDays = scheduleDays,
        scheduleStart = scheduleStart,
        scheduleEnd = scheduleEnd,
        template = template.text.ifBlank { MessageTemplate.DEFAULT },
        sendSubId = sendSubId,
        receiveSubId = receiveSubId,
        delaySeconds = delaySeconds,
        replyRelay = replyRelay,
        createdAt = if (original.createdAt == 0L) System.currentTimeMillis() else original.createdAt,
    )

    /** Inserts a {placeholder} at the cursor. */
    fun insertToken(token: String) {
        val value = template
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(0, value.text.length)
        val text = value.text.replaceRange(start, end, token)
        template = TextFieldValue(text, TextRange(start + token.length))
    }

    fun applyPreset(preset: MessageTemplate.Preset) {
        template = TextFieldValue(preset.template, TextRange(preset.template.length))
    }

    fun setParties(target: PickerTarget, parties: List<Party>) {
        val list = when (target) {
            PickerTarget.SOURCES -> sources
            PickerTarget.EXCLUSIONS -> exclusions
            PickerTarget.DESTINATIONS -> destinations
        }
        list.clear()
        list.addAll(parties.distinctBy { Phones.key(it.address) })
    }

    fun parties(target: PickerTarget): List<Party> = when (target) {
        PickerTarget.SOURCES -> sources
        PickerTarget.EXCLUSIONS -> exclusions
        PickerTarget.DESTINATIONS -> destinations
    }
}

enum class PickerTarget { SOURCES, EXCLUSIONS, DESTINATIONS }
