package com.example.notice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(RingService.EXTRA_ID, -1L)
        if (id < 0L) return
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE, AlarmScheduler.ACTION_RING -> {
                try {
                    RingService.start(context, RingService.ACTION_RING, id)
                } catch (_: Exception) {
                    // Foreground-service start was blocked (rare, non-exact alarm on 12+):
                    // surface the alert notification directly and retry ringing in 1 minute.
                    Notifier.ensureChannels(context)
                    AlarmStore.setActiveAlarm(context, id, true)
                    Notifier.show(context, Notifier.ringingNotification(context, id))
                    AlarmScheduler.scheduleReRing(context, id, 1)
                }
            }
        }
    }
}
