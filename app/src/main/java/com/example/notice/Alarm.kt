package com.example.notice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Alarm(
    val id: Long,
    var hour: Int,
    var minute: Int,
    var label: String = "",
    var enabled: Boolean = true,
    var intervalMinutes: Int = 5,
    var days: MutableSet<Int> = mutableSetOf(),
    /** Empty = system default alarm sound; RINGTONE_SILENT = vibrate only; else a ringtone/content URI. */
    var ringtoneUri: String = "",
    /** Cached display name for the ringtone (may be empty). */
    var ringtoneName: String = ""
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("enabled", enabled)
        put("interval", intervalMinutes)
        put("days", JSONArray(days.toIntArray()))
        put("ringtoneUri", ringtoneUri)
        put("ringtoneName", ringtoneName)
    }

    fun daysDisplay(context: Context): String {
        if (days.isEmpty()) return context.getString(R.string.once)
        if (days.size == 7) return context.getString(R.string.every_day)
        val names = context.resources.getStringArray(R.array.week_days)
        return days.sorted().joinToString(" ") { names[it - 1] }
    }

    companion object {
        /** Sentinel ringtoneUri meaning "no sound, vibration only". */
        const val RINGTONE_SILENT = "silent"

        fun fromJson(o: JSONObject): Alarm = Alarm(
            id = o.getLong("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            label = o.optString("label", ""),
            enabled = o.optBoolean("enabled", true),
            intervalMinutes = o.optInt("interval", 5),
            days = mutableSetOf<Int>().apply {
                val a = o.optJSONArray("days")
                if (a != null) for (i in 0 until a.length()) add(a.getInt(i))
            },
            ringtoneUri = o.optString("ringtoneUri", ""),
            ringtoneName = o.optString("ringtoneName", "")
        )
    }
}
