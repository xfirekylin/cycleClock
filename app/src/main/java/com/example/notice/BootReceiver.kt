package com.example.notice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-registers all alarms after reboot / time change / app update.
 * If an alarm was ringing or awaiting re-ring when the device went down,
 * start ringing again — the occurrence was never dismissed.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val active = AlarmStore.activeAlarmId(context)
                if (active >= 0) {
                    try {
                        RingService.start(context, RingService.ACTION_RING, active)
                    } catch (_: Exception) {
                        AlarmScheduler.scheduleReRing(context, active, 1)
                    }
                }
                AlarmScheduler.rescheduleAll(context)
            }
        }
    }
}
