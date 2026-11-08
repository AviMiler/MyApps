package com.myappstore.alarmclock

import android.content.Context
import org.json.JSONArray

/** Persists alarms as a JSON array in SharedPreferences. */
object AlarmStore {

    private const val PREFS = "alarms"
    private const val KEY_ALARMS = "list"
    private const val KEY_NEXT_ID = "next_id"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): MutableList<Alarm> {
        val raw = prefs(context).getString(KEY_ALARMS, null) ?: return mutableListOf()
        val array = JSONArray(raw)
        val out = mutableListOf<Alarm>()
        for (i in 0 until array.length()) out.add(Alarm.fromJson(array.getJSONObject(i)))
        out.sortWith(compareBy({ it.hour }, { it.minute }))
        return out
    }

    fun get(context: Context, id: Int): Alarm? = all(context).firstOrNull { it.id == id }

    fun save(context: Context, alarm: Alarm) {
        val list = all(context)
        val index = list.indexOfFirst { it.id == alarm.id }
        if (index >= 0) list[index] = alarm else list.add(alarm)
        writeAll(context, list)
    }

    fun delete(context: Context, id: Int) {
        writeAll(context, all(context).filterNot { it.id == id }.toMutableList())
    }

    fun newId(context: Context): Int {
        val p = prefs(context)
        val id = p.getInt(KEY_NEXT_ID, 1)
        p.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    private fun writeAll(context: Context, list: List<Alarm>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ALARMS, array.toString()).apply()
    }
}
