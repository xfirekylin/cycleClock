package com.example.notice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    /** Initial fire of an alarm at its scheduled time. */
    const val ACTION_FIRE = "com.example.notice.action.FIRE"

    /** Re-ring of an alarm whose ringtone was stopped but not dismissed. */
    const val ACTION_RING = "com.example.notice.action.RE_RING"

    private const val KIND_FIRE = 0
    private const val KIND_RING = 1

    private fun am(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Next wall-clock time this alarm should fire at, or null if it can never
     * fire again (should not happen in practice).
     */
    fun nextTrigger(alarm: Alarm, now: Long = System.currentTimeMillis()): Long? {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (alarm.days.isEmpty()) {
            // one-shot: if the moment already passed, roll to tomorrow
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        repeat(8) {
            if (alarm.days.contains(cal.get(Calendar.DAY_OF_WEEK)) && cal.timeInMillis > now) {
                return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    fun schedule(context: Context, alarm: Alarm): Long? {
        cancel(context, alarm.id)
        val at = nextTrigger(alarm) ?: return null
        setAlarm(context, at, firePendingIntent(context, alarm.id))
        return at
    }

    fun cancel(context: Context, id: Long) {
        am(context).cancel(firePendingIntent(context, id))
    }

    /** Schedule the next re-ring X minutes from now (X = the alarm's interval). */
    fun scheduleReRing(context: Context, id: Long, intervalMinutes: Int) {
        val at = System.currentTimeMillis() + intervalMinutes * 60_000L
        AlarmStore.setNextRingAt(context, at)
        setAlarm(context, at, ringPendingIntent(context, id))
    }

    fun cancelReRing(context: Context, id: Long) {
        am(context).cancel(ringPendingIntent(context, id))
        AlarmStore.setNextRingAt(context, 0L)
    }

    fun rescheduleAll(context: Context) {
        AlarmStore.all(context).filter { it.enabled }.forEach { schedule(context, it) }
    }

    private fun setAlarm(context: Context, at: Long, pi: PendingIntent) {
        val manager = am(context)
        val canExact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        when {
            canExact && Build.VERSION.SDK_INT >= 23 ->
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            Build.VERSION.SDK_INT >= 23 ->
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else -> manager.setExact(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun firePendingIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode(id, KIND_FIRE),
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(RingService.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun ringPendingIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode(id, KIND_RING),
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_RING)
                .putExtra(RingService.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(id: Long, kind: Int): Int =
        ((id % 500_000_000L).toInt()) + kind * 500_000_000
}
