package com.example.notice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {

    const val NOTIF_ID = 1001
    private const val CHANNEL_RINGING = "ringing"
    private const val CHANNEL_PENDING = "pending"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RINGING,
                context.getString(R.string.channel_ringing),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PENDING,
                context.getString(R.string.channel_pending),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    fun ringingNotification(context: Context, alarmId: Long): Notification {
        val alarm = if (alarmId >= 0) AlarmStore.get(context, alarmId) else null
        val title = alarm?.label?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)

        // Full-screen alert shows only "stop ringtone"; dismissing this occurrence
        // happens exclusively inside the app's alarm list.
        val fullScreenPi = alertActivityPi(context, alarmId, 0)
        val contentPi = mainActivityPi(context, alarmId, 3)
        val stopPi = servicePi(context, RingService.ACTION_STOP_RING, alarmId, 1)

        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(contentPi)
            .addAction(0, context.getString(R.string.action_stop_ring), stopPi)
            .build()
    }

    fun pendingNotification(context: Context, alarmId: Long): Notification {
        val nextAt = AlarmStore.nextRingAt(context)
        val minutes =
            if (nextAt > 0) ((nextAt - System.currentTimeMillis() + 59_999) / 60_000).toInt().coerceAtLeast(1)
            else 1

        val contentPi = mainActivityPi(context, alarmId, 3)

        return NotificationCompat.Builder(context, CHANNEL_PENDING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.notif_pending_title))
            .setContentText(context.getString(R.string.notif_pending_text, minutes))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .build()
    }

    fun show(context: Context, notification: Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notification) }
    }

    private fun mainActivityPi(context: Context, alarmId: Long, salt: Int): PendingIntent =
        PendingIntent.getActivity(
            context, piCode(alarmId, salt),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun alertActivityPi(context: Context, alarmId: Long, salt: Int): PendingIntent =
        PendingIntent.getActivity(
            context, piCode(alarmId, salt),
            Intent(context, AlarmAlertActivity::class.java)
                .putExtra(AlarmAlertActivity.EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun servicePi(context: Context, action: String, alarmId: Long, salt: Int): PendingIntent =
        PendingIntent.getService(
            context, piCode(alarmId, salt),
            Intent(context, RingService::class.java)
                .setAction(action)
                .putExtra(RingService.EXTRA_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun piCode(id: Long, salt: Int): Int = ((id % 100_000L).toInt() + salt * 7)
}
