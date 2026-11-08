package com.myappstore.alarmclock

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * A single alarm.
 *
 * [ringSeconds] is the total ring duration in seconds and may be any positive
 * number - the edit screen collects minutes and seconds separately and stores
 * the sum here.
 */
data class Alarm(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var label: String = "",
    /** Days the alarm repeats on, as [Calendar.SUNDAY]..[Calendar.SATURDAY]. Empty = one shot. */
    var days: MutableSet<Int> = mutableSetOf(),
    var enabled: Boolean = true,
    var ringSeconds: Int = 60,
    var volumePercent: Int = 100,
    /** Force the alarm stream to [volumePercent] and ignore silent/vibrate/Do-Not-Disturb. */
    var overrideSystemSound: Boolean = true,
    var vibrate: Boolean = true,
    var soundUri: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("days", JSONArray(days.toList()))
        put("enabled", enabled)
        put("ringSeconds", ringSeconds)
        put("volumePercent", volumePercent)
        put("overrideSystemSound", overrideSystemSound)
        put("vibrate", vibrate)
        put("soundUri", soundUri ?: JSONObject.NULL)
    }

    /** Next time this alarm should fire, in epoch millis. */
    fun nextTriggerMillis(from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (days.isEmpty()) {
            if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }
        for (i in 0..7) {
            if (days.contains(c.get(Calendar.DAY_OF_WEEK)) && c.timeInMillis > from) {
                return c.timeInMillis
            }
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val daysArray = o.optJSONArray("days") ?: JSONArray()
            val days = mutableSetOf<Int>()
            for (i in 0 until daysArray.length()) days.add(daysArray.getInt(i))
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                label = o.optString("label", ""),
                days = days,
                enabled = o.optBoolean("enabled", true),
                ringSeconds = o.optInt("ringSeconds", 60),
                volumePercent = o.optInt("volumePercent", 100),
                overrideSystemSound = o.optBoolean("overrideSystemSound", true),
                vibrate = o.optBoolean("vibrate", true),
                soundUri = if (o.isNull("soundUri")) null else o.optString("soundUri")
            )
        }
    }
}
