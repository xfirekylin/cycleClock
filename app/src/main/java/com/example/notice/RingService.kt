package com.example.notice

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

/**
 * Foreground service that owns the ring session of an alarm.
 *
 * Two distinct operations, per spec:
 *  - ACTION_STOP_RING: silence the ringtone now; the alarm stays active and a
 *    re-ring is scheduled X minutes later. This repeats forever...
 *  - ACTION_DISMISS: ...until the user dismisses this alarm occurrence, which
 *    cancels all pending re-rings and reschedules/disables the alarm itself.
 */
class RingService : Service() {

    companion object {
        const val EXTRA_ID = "alarm_id"
        const val ACTION_RING = "com.example.notice.action.RING"
        const val ACTION_STOP_RING = "com.example.notice.action.STOP_RING"
        const val ACTION_DISMISS = "com.example.notice.action.DISMISS"

        fun start(context: Context, action: String, id: Long) {
            val i = Intent(context, RingService::class.java)
                .setAction(action)
                .putExtra(EXTRA_ID, id)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }
    }

    private var alarmId: Long = -1L
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val extraId = intent.getLongExtra(EXTRA_ID, -1L)
                if (extraId >= 0) {
                    alarmId = extraId
                    AlarmStore.setActiveAlarm(this, extraId, true)
                }
                if (alarmId < 0) {
                    exitQuietly()
                    return START_NOT_STICKY
                }
                startForeground(Notifier.NOTIF_ID, Notifier.ringingNotification(this, alarmId))
                startRinging()
            }
            ACTION_STOP_RING -> {
                if (alarmId < 0) alarmId = AlarmStore.activeAlarmId(this)
                if (alarmId < 0) {
                    exitQuietly()
                    return START_NOT_STICKY
                }
                stopRingingAndScheduleReRing()
            }
            ACTION_DISMISS -> {
                if (alarmId < 0) alarmId = AlarmStore.activeAlarmId(this)
                if (alarmId >= 0) dismiss()
                else exitQuietly()
                return START_NOT_STICKY
            }
            else -> {
                // Restarted by the system (START_STICKY) or stale launch:
                // restore the previous ring session if any.
                val saved = AlarmStore.activeAlarmId(this)
                if (saved < 0) {
                    exitQuietly()
                    return START_NOT_STICKY
                }
                alarmId = saved
                if (AlarmStore.wasRinging(this)) {
                    startForeground(Notifier.NOTIF_ID, Notifier.ringingNotification(this, saved))
                    startRinging()
                } else {
                    startForeground(Notifier.NOTIF_ID, Notifier.pendingNotification(this, saved))
                }
            }
        }
        return START_STICKY
    }

    private fun startRinging() {
        AlarmStore.setActiveAlarm(this, alarmId, true)
        startPlaying()
        startVibrating()
    }

    private fun stopRingingAndScheduleReRing() {
        stopPlaying()
        stopVibrating()
        val alarm = AlarmStore.get(this, alarmId)
        val interval = (alarm?.intervalMinutes ?: 5).coerceIn(1, 24 * 60)
        AlarmStore.setActiveAlarm(this, alarmId, false)
        AlarmScheduler.scheduleReRing(this, alarmId, interval)
        startForeground(Notifier.NOTIF_ID, Notifier.pendingNotification(this, alarmId))
    }

    private fun dismiss() {
        val id = alarmId
        if (id >= 0) {
            AlarmScheduler.cancelReRing(this, id)
            AlarmStore.setActiveAlarm(this, -1, false)
            val alarm = AlarmStore.get(this, id)
            if (alarm != null && alarm.enabled) {
                if (alarm.days.isEmpty()) {
                    alarm.enabled = false
                    AlarmStore.save(this, alarm)
                } else {
                    AlarmScheduler.schedule(this, alarm)
                }
            }
        }
        stopPlaying()
        stopVibrating()
        alarmId = -1L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Satisfy the startForeground contract, then go away silently. */
    private fun exitQuietly() {
        val n: Notification = Notifier.pendingNotification(this, -1L)
        startForeground(Notifier.NOTIF_ID, n)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startPlaying() {
        stopPlaying()
        val alarm = if (alarmId >= 0) AlarmStore.get(this, alarmId) else null
        if (alarm?.ringtoneUri == Alarm.RINGTONE_SILENT) return // silent: vibration only

        val defaultUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        val customUri = alarm?.ringtoneUri?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

        play(customUri ?: defaultUri, if (customUri != null) defaultUri else null)
    }

    /** Play [uri]; if it fails to load, retry once with [fallback] (e.g. a SAF file deleted by the user). */
    private fun play(uri: Uri, fallback: Uri?) {
        try {
            player = MediaPlayer().apply {
                setDataSource(this@RingService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setWakeMode(this@RingService, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { mp, _, _ ->
                    runCatching { mp.release() }
                    player = null
                    if (fallback != null) play(fallback, null)
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            player = null
            if (fallback != null) play(fallback, null)
        }
    }

    private fun stopPlaying() {
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
        player = null
    }

    private fun startVibrating() {
        val v = vibrator ?: return
        val pattern = longArrayOf(0, 800, 600)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        }
    }

    private fun stopVibrating() {
        runCatching { vibrator?.cancel() }
    }

    override fun onDestroy() {
        stopPlaying()
        stopVibrating()
        super.onDestroy()
    }
}
