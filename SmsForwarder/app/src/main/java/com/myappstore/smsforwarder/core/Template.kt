package com.myappstore.smsforwarder.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The text that is actually sent to the recipient, built from a user-editable
 * template with {placeholders}.
 */
object MessageTemplate {

    const val SENDER = "{sender}"
    const val MESSAGE = "{message}"
    const val CODE = "{code}"
    const val TIME = "{time}"
    const val DATE = "{date}"
    const val NUMBER = "{number}"
    const val NAME = "{name}"
    const val ROUTE = "{route}"
    const val SIM = "{sim}"

    const val DEFAULT = "📩 {sender}\n{message}"

    /** Ready-made formats offered in the editor. */
    enum class Preset(val template: String) {
        CLASSIC(DEFAULT),
        DETAILED("📩 {sender} · {time}\n{message}"),
        CODE_FIRST("🔑 {code} · {sender}\n{message}"),
        ONE_LINE("[{sender}] {message}"),
        PLAIN("{message}"),
    }

    /** Placeholders shown as insertable chips, in display order. */
    val placeholders = listOf(SENDER, MESSAGE, CODE, TIME, DATE, NUMBER, NAME, ROUTE, SIM)

    data class Values(
        val senderName: String?,
        val senderAddress: String,
        val message: String,
        val code: String?,
        val receivedAt: Long,
        val zone: ZoneId,
        val routeName: String,
        val simName: String?,
    )

    private val token = Regex("""\{([a-z]+)\}""")
    private val codeWithDecoration = Regex("""🔑\s*\{code\}\s*(?:[·|:–-]\s*)?""")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM")

    fun render(template: String, values: Values): String {
        val source = if (values.code.isNullOrEmpty()) template.replace(codeWithDecoration, "") else template
        val time = Instant.ofEpochMilli(values.receivedAt).atZone(values.zone)
        val senderLabel = values.senderName?.takeIf { it.isNotBlank() } ?: Phones.pretty(values.senderAddress)
        val rendered = token.replace(source) { match ->
            when (match.groupValues[1]) {
                "sender" -> senderLabel
                "message" -> values.message
                "code" -> values.code.orEmpty()
                "time" -> timeFormat.format(time)
                "date" -> dateFormat.format(time)
                "number" -> Phones.pretty(values.senderAddress)
                "name" -> values.senderName.orEmpty()
                "route" -> values.routeName
                "sim" -> values.simName.orEmpty()
                else -> match.value
            }
        }
        return rendered.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** Joins several already-rendered messages into one digest SMS. */
    fun digest(header: String, rendered: List<String>): String =
        (listOf(header) + rendered).joinToString(separator = "\n\n")
}
