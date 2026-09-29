package com.myappstore.smsforwarder.data

import android.content.Context
import com.myappstore.smsforwarder.core.Cities
import com.myappstore.smsforwarder.core.Days
import com.myappstore.smsforwarder.core.HoldPolicy
import com.myappstore.smsforwarder.core.HoldRules
import com.myappstore.smsforwarder.core.RestCalendar
import com.myappstore.smsforwarder.core.TimeWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

object ThemeMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

data class AppSettings(
    val masterEnabled: Boolean = true,
    /** Epoch millis until which forwarding is paused; [HoldPolicy.FOREVER] = until resumed. */
    val pausedUntil: Long = 0L,
    val holdWhilePaused: Boolean = true,
    val quietEnabled: Boolean = false,
    val quietStart: Int = 22 * 60 + 30,
    val quietEnd: Int = 7 * 60,
    val quietDays: Int = Days.ALL,
    val holdDuringQuiet: Boolean = true,
    val restEnabled: Boolean = false,
    val restCityId: String = Cities.default.id,
    val candleMinutes: Int = Cities.default.candleMinutes,
    val havdalahMinutes: Int = 40,
    val restIncludesHolidays: Boolean = true,
    val combineHeld: Boolean = true,
    val dailyLimit: Int = 100,
    val loopGuard: Boolean = true,
    val replyPrefix: String = "#",
    val notifyEachForward: Boolean = false,
    val notifyProblems: Boolean = true,
    val hideBodies: Boolean = false,
    val retentionDays: Int = 30,
    val themeMode: Int = ThemeMode.SYSTEM,
    val defaultSubId: Int = -1,
    val onboardingDone: Boolean = false,
    val lastCleanupAt: Long = 0L,
) {
    fun isPaused(now: Long = System.currentTimeMillis()) = pausedUntil > now

    fun restCalendar(zone: ZoneId, holidays: (LocalDate) -> Boolean): RestCalendar? {
        if (!restEnabled) return null
        return RestCalendar(
            city = Cities.byId(restCityId),
            zone = zone,
            candleMinutes = candleMinutes,
            havdalahMinutes = havdalahMinutes,
            isHoliday = if (restIncludesHolidays) holidays else { _ -> false },
        )
    }

    fun quietWindow(): TimeWindow? =
        if (quietEnabled) TimeWindow(quietDays, quietStart, quietEnd) else null

    fun holdRules(zone: ZoneId, holidays: (LocalDate) -> Boolean) = HoldRules(
        pausedUntil = pausedUntil,
        holdWhilePaused = holdWhilePaused,
        quietHours = quietWindow(),
        holdDuringQuiet = holdDuringQuiet,
        rest = restCalendar(zone, holidays),
    )
}

/**
 * Settings kept in SharedPreferences and exposed as a [StateFlow] so the UI, the
 * receivers and the Quick Settings tile all see the same values.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("halaa_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    private val listeners = mutableListOf<(AppSettings, AppSettings) -> Unit>()

    val flow: StateFlow<AppSettings> = state.asStateFlow()

    val value: AppSettings get() = state.value

    /** [listener] gets (old, new) after every change. */
    fun addListener(listener: (AppSettings, AppSettings) -> Unit) {
        synchronized(listeners) { listeners += listener }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val old: AppSettings
        val new: AppSettings
        synchronized(this) {
            old = state.value
            new = transform(old)
            if (new == old) return
            save(new)
            state.value = new
        }
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it(old, new) }
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            masterEnabled = prefs.getBoolean("master", d.masterEnabled),
            pausedUntil = prefs.getLong("pausedUntil", d.pausedUntil),
            holdWhilePaused = prefs.getBoolean("holdWhilePaused", d.holdWhilePaused),
            quietEnabled = prefs.getBoolean("quietEnabled", d.quietEnabled),
            quietStart = prefs.getInt("quietStart", d.quietStart),
            quietEnd = prefs.getInt("quietEnd", d.quietEnd),
            quietDays = prefs.getInt("quietDays", d.quietDays),
            holdDuringQuiet = prefs.getBoolean("holdDuringQuiet", d.holdDuringQuiet),
            restEnabled = prefs.getBoolean("restEnabled", d.restEnabled),
            restCityId = prefs.getString("restCity", d.restCityId) ?: d.restCityId,
            candleMinutes = prefs.getInt("candleMinutes", d.candleMinutes),
            havdalahMinutes = prefs.getInt("havdalahMinutes", d.havdalahMinutes),
            restIncludesHolidays = prefs.getBoolean("restHolidays", d.restIncludesHolidays),
            combineHeld = prefs.getBoolean("combineHeld", d.combineHeld),
            dailyLimit = prefs.getInt("dailyLimit", d.dailyLimit),
            loopGuard = prefs.getBoolean("loopGuard", d.loopGuard),
            replyPrefix = prefs.getString("replyPrefix", d.replyPrefix) ?: d.replyPrefix,
            notifyEachForward = prefs.getBoolean("notifyEach", d.notifyEachForward),
            notifyProblems = prefs.getBoolean("notifyProblems", d.notifyProblems),
            hideBodies = prefs.getBoolean("hideBodies", d.hideBodies),
            retentionDays = prefs.getInt("retentionDays", d.retentionDays),
            themeMode = prefs.getInt("themeMode", d.themeMode),
            defaultSubId = prefs.getInt("defaultSubId", d.defaultSubId),
            onboardingDone = prefs.getBoolean("onboardingDone", d.onboardingDone),
            lastCleanupAt = prefs.getLong("lastCleanupAt", d.lastCleanupAt),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean("master", s.masterEnabled)
            .putLong("pausedUntil", s.pausedUntil)
            .putBoolean("holdWhilePaused", s.holdWhilePaused)
            .putBoolean("quietEnabled", s.quietEnabled)
            .putInt("quietStart", s.quietStart)
            .putInt("quietEnd", s.quietEnd)
            .putInt("quietDays", s.quietDays)
            .putBoolean("holdDuringQuiet", s.holdDuringQuiet)
            .putBoolean("restEnabled", s.restEnabled)
            .putString("restCity", s.restCityId)
            .putInt("candleMinutes", s.candleMinutes)
            .putInt("havdalahMinutes", s.havdalahMinutes)
            .putBoolean("restHolidays", s.restIncludesHolidays)
            .putBoolean("combineHeld", s.combineHeld)
            .putInt("dailyLimit", s.dailyLimit)
            .putBoolean("loopGuard", s.loopGuard)
            .putString("replyPrefix", s.replyPrefix)
            .putBoolean("notifyEach", s.notifyEachForward)
            .putBoolean("notifyProblems", s.notifyProblems)
            .putBoolean("hideBodies", s.hideBodies)
            .putInt("retentionDays", s.retentionDays)
            .putInt("themeMode", s.themeMode)
            .putInt("defaultSubId", s.defaultSubId)
            .putBoolean("onboardingDone", s.onboardingDone)
            .putLong("lastCleanupAt", s.lastCleanupAt)
            .apply()
    }
}
