package com.example.notice

import android.content.Context
import org.json.JSONArray

/**
 * Persistence for alarms (JSON in SharedPreferences) plus the transient
 * "currently active ring session" state, which survives process death so a
 * not-yet-dismissed alarm keeps re-ringing after a service restart or reboot.
 */
object AlarmStore {

    private const val PREFS = "alarm_store"
    private const val KEY_JSON = "alarms_json"
    private const val KEY_ACTIVE_ID = "active_alarm_id"
    private const val KEY_ACTIVE_RINGING = "active_alarm_ringing"
    private const val KEY_NEXT_RING_AT = "next_ring_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): MutableList<Alarm> {
        val json = prefs(context).getString(KEY_JSON, null) ?: return mutableListOf()
        val list = mutableListOf<Alarm>()
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                runCatching { list.add(Alarm.fromJson(arr.getJSONObject(i))) }
            }
        }
        return list
    }

    fun get(context: Context, id: Long): Alarm? = all(context).firstOrNull { it.id == id }

    fun save(context: Context, alarm: Alarm) {
        val list = all(context)
        val idx = list.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) list[idx] = alarm else list.add(alarm)
        write(context, list)
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filter { it.id != id })
    }

    private fun write(context: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_JSON, arr.toString()).apply()
    }

    fun activeAlarmId(context: Context): Long = prefs(context).getLong(KEY_ACTIVE_ID, -1L)

    fun wasRinging(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE_RINGING, false)

    fun setActiveAlarm(context: Context, id: Long, ringing: Boolean) {
        prefs(context).edit()
            .putLong(KEY_ACTIVE_ID, id)
            .putBoolean(KEY_ACTIVE_RINGING, ringing)
            .apply()
    }

    fun nextRingAt(context: Context): Long = prefs(context).getLong(KEY_NEXT_RING_AT, 0L)

    fun setNextRingAt(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_NEXT_RING_AT, at).apply()
    }
}
