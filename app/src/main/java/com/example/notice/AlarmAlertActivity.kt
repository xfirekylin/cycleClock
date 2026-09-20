package com.example.notice

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.notice.databinding.ActivityAlertBinding
import java.util.Calendar
import java.util.Locale

/**
 * Full-screen alert shown over the lock screen when an alarm fires.
 *
 * Per spec, this screen can ONLY stop the ringtone — the ring will come back
 * every X minutes until the user opens the app and dismisses this alarm
 * occurrence from the alarm list (see the banner in MainActivity).
 */
class AlarmAlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extraId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        val alarmId = if (extraId >= 0) extraId else AlarmStore.activeAlarmId(this)
        val alarm = if (alarmId >= 0) AlarmStore.get(this, alarmId) else null
        val now = Calendar.getInstance()

        binding.tvClockTime.text = String.format(
            Locale.getDefault(), "%02d:%02d",
            alarm?.hour ?: now.get(Calendar.HOUR_OF_DAY),
            alarm?.minute ?: now.get(Calendar.MINUTE)
        )
        binding.tvLabel.text = alarm?.label?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
        binding.tvNote.text = getString(R.string.alert_note, alarm?.intervalMinutes ?: 5)

        binding.btnStopRing.setOnClickListener {
            runCatching { RingService.start(this, RingService.ACTION_STOP_RING, alarmId) }
            finish()
        }
    }
}
