package com.myappstore.smsforwarder.data

import org.json.JSONArray
import org.json.JSONObject

/** Export/import of routes and settings as a readable JSON file. */
object Backup {

    private const val FORMAT = "halaa-backup"
    private const val VERSION = 1

    class Parsed(val routes: List<Route>, val settings: AppSettings?)

    fun export(routes: List<Route>, settings: AppSettings): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("routes", JSONArray().apply { routes.forEach { put(route(it)) } })
        put("settings", settings(settings))
    }.toString(2)

    /** Throws [IllegalArgumentException] when the text is not a backup made by this app. */
    fun parse(text: String): Parsed {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("not json", e)
        }
        require(root.optString("format") == FORMAT) { "not a backup" }
        val routes = root.optJSONArray("routes")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::route) }
        }.orEmpty()
        val settings = root.optJSONObject("settings")?.let(::settings)
        return Parsed(routes, settings)
    }

    private fun route(r: Route) = JSONObject().apply {
        put("name", r.name)
        put("color", r.colorIndex)
        put("enabled", r.enabled)
        put("sourceMode", r.sourceMode)
        put("sources", Json.parties(r.sources))
        put("exclusions", Json.parties(r.exclusions))
        put("destinations", Json.parties(r.destinations))
        put("include", JSONArray(r.includeWords))
        put("exclude", JSONArray(r.excludeWords))
        put("codesOnly", r.codesOnly)
        put("scheduleEnabled", r.scheduleEnabled)
        put("scheduleDays", r.scheduleDays)
        put("scheduleStart", r.scheduleStart)
        put("scheduleEnd", r.scheduleEnd)
        put("template", r.template)
        put("delaySeconds", r.delaySeconds)
        put("replyRelay", r.replyRelay)
    }

    private fun route(o: JSONObject): Route {
        val d = Route()
        return Route(
            name = o.optString("name"),
            colorIndex = o.optInt("color", d.colorIndex),
            enabled = o.optBoolean("enabled", d.enabled),
            sourceMode = o.optInt("sourceMode", d.sourceMode),
            sources = Json.parties(o.optJSONArray("sources")),
            exclusions = Json.parties(o.optJSONArray("exclusions")),
            destinations = Json.parties(o.optJSONArray("destinations")),
            includeWords = Json.words(o.optJSONArray("include")),
            excludeWords = Json.words(o.optJSONArray("exclude")),
            codesOnly = o.optBoolean("codesOnly", d.codesOnly),
            scheduleEnabled = o.optBoolean("scheduleEnabled", d.scheduleEnabled),
            scheduleDays = o.optInt("scheduleDays", d.scheduleDays),
            scheduleStart = o.optInt("scheduleStart", d.scheduleStart),
            scheduleEnd = o.optInt("scheduleEnd", d.scheduleEnd),
            template = o.optString("template", d.template).ifBlank { d.template },
            delaySeconds = o.optInt("delaySeconds", d.delaySeconds),
            replyRelay = o.optBoolean("replyRelay", d.replyRelay),
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun settings(s: AppSettings) = JSONObject().apply {
        put("holdWhilePaused", s.holdWhilePaused)
        put("quietEnabled", s.quietEnabled)
        put("quietStart", s.quietStart)
        put("quietEnd", s.quietEnd)
        put("quietDays", s.quietDays)
        put("holdDuringQuiet", s.holdDuringQuiet)
        put("restEnabled", s.restEnabled)
        put("restCity", s.restCityId)
        put("candleMinutes", s.candleMinutes)
        put("havdalahMinutes", s.havdalahMinutes)
        put("restHolidays", s.restIncludesHolidays)
        put("combineHeld", s.combineHeld)
        put("dailyLimit", s.dailyLimit)
        put("loopGuard", s.loopGuard)
        put("replyPrefix", s.replyPrefix)
        put("notifyEach", s.notifyEachForward)
        put("notifyProblems", s.notifyProblems)
        put("hideBodies", s.hideBodies)
        put("retentionDays", s.retentionDays)
        put("themeMode", s.themeMode)
    }

    private fun settings(o: JSONObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            holdWhilePaused = o.optBoolean("holdWhilePaused", d.holdWhilePaused),
            quietEnabled = o.optBoolean("quietEnabled", d.quietEnabled),
            quietStart = o.optInt("quietStart", d.quietStart),
            quietEnd = o.optInt("quietEnd", d.quietEnd),
            quietDays = o.optInt("quietDays", d.quietDays),
            holdDuringQuiet = o.optBoolean("holdDuringQuiet", d.holdDuringQuiet),
            restEnabled = o.optBoolean("restEnabled", d.restEnabled),
            restCityId = o.optString("restCity", d.restCityId),
            candleMinutes = o.optInt("candleMinutes", d.candleMinutes),
            havdalahMinutes = o.optInt("havdalahMinutes", d.havdalahMinutes),
            restIncludesHolidays = o.optBoolean("restHolidays", d.restIncludesHolidays),
            combineHeld = o.optBoolean("combineHeld", d.combineHeld),
            dailyLimit = o.optInt("dailyLimit", d.dailyLimit),
            loopGuard = o.optBoolean("loopGuard", d.loopGuard),
            replyPrefix = o.optString("replyPrefix", d.replyPrefix).ifBlank { d.replyPrefix },
            notifyEachForward = o.optBoolean("notifyEach", d.notifyEachForward),
            notifyProblems = o.optBoolean("notifyProblems", d.notifyProblems),
            hideBodies = o.optBoolean("hideBodies", d.hideBodies),
            retentionDays = o.optInt("retentionDays", d.retentionDays),
            themeMode = o.optInt("themeMode", d.themeMode),
        )
    }
}
