package com.example.notice

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notice.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Refresh the active-alarm banner (countdown text) while the screen is visible. */
    private val bannerTick = object : Runnable {
        override fun run() {
            updateActiveBanner()
            binding.root.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = AlarmAdapter(
            onClick = { alarm -> startActivity(EditAlarmActivity.edit(this, alarm.id)) },
            onLongClick = { alarm -> confirmDelete(alarm) },
            onToggle = { alarm, on -> toggleAlarm(alarm, on) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.fab.setOnClickListener { startActivity(EditAlarmActivity.create(this)) }

        // The ONLY place this alarm occurrence can be dismissed.
        binding.btnActiveDismiss.setOnClickListener { dismissActiveAlarm() }
        binding.btnActiveStopRing.setOnClickListener { stopActiveRing() }

        requestNeededPermissions()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        binding.root.removeCallbacks(bannerTick)
        binding.root.postDelayed(bannerTick, 30_000)
    }

    override fun onPause() {
        binding.root.removeCallbacks(bannerTick)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_battery_guide -> {
            startActivity(Intent(this, BatteryGuideActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun refresh() {
        val alarms = AlarmStore.all(this)
        adapter.submit(alarms)
        binding.emptyView.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        updateActiveBanner()
    }

    private fun updateActiveBanner() {
        val id = AlarmStore.activeAlarmId(this)
        if (id < 0) {
            binding.activeBanner.visibility = View.GONE
            return
        }
        val alarm = AlarmStore.get(this, id)
        binding.activeBanner.visibility = View.VISIBLE
        binding.tvActiveTitle.text =
            alarm?.label?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
        if (AlarmStore.wasRinging(this)) {
            binding.tvActiveStatus.text = getString(R.string.banner_ringing)
            binding.btnActiveStopRing.visibility = View.VISIBLE
        } else {
            val nextAt = AlarmStore.nextRingAt(this)
            val minutes =
                if (nextAt > 0) ((nextAt - System.currentTimeMillis() + 59_999) / 60_000).toInt().coerceAtLeast(1)
                else 1
            binding.tvActiveStatus.text = getString(R.string.banner_muted, minutes)
            binding.btnActiveStopRing.visibility = View.GONE
        }
    }

    private fun dismissActiveAlarm() {
        val id = AlarmStore.activeAlarmId(this)
        if (id >= 0) {
            runCatching { RingService.start(this, RingService.ACTION_DISMISS, id) }
            binding.root.postDelayed({ refresh() }, 300)
        }
    }

    private fun stopActiveRing() {
        val id = AlarmStore.activeAlarmId(this)
        if (id >= 0) {
            runCatching { RingService.start(this, RingService.ACTION_STOP_RING, id) }
            binding.root.postDelayed({ refresh() }, 300)
        }
    }

    private fun toggleAlarm(alarm: Alarm, on: Boolean) {
        alarm.enabled = on
        AlarmStore.save(this, alarm)
        if (on) {
            AlarmScheduler.schedule(this, alarm)
        } else {
            AlarmScheduler.cancel(this, alarm.id)
            AlarmScheduler.cancelReRing(this, alarm.id)
            if (AlarmStore.activeAlarmId(this) == alarm.id) {
                runCatching { RingService.start(this, RingService.ACTION_DISMISS, alarm.id) }
            }
        }
        refresh()
    }

    private fun confirmDelete(alarm: Alarm) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_msg)
            .setPositiveButton(R.string.confirm) { _, _ ->
                AlarmStore.delete(this, alarm.id)
                AlarmScheduler.cancel(this, alarm.id)
                AlarmScheduler.cancelReRing(this, alarm.id)
                if (AlarmStore.activeAlarmId(this) == alarm.id) {
                    runCatching { RingService.start(this, RingService.ACTION_DISMISS, alarm.id) }
                }
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            if (am?.canScheduleExactAlarms() == false) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.exact_alarm_dialog_title)
                    .setMessage(R.string.exact_alarm_dialog_msg)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        runCatching {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { maybeShowBatteryGuide() }
                    .show()
            } else {
                maybeShowBatteryGuide()
            }
        } else {
            maybeShowBatteryGuide()
        }
    }

    /** Show the battery-optimization guide once on first launch, if not whitelisted yet. */
    private fun maybeShowBatteryGuide() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("battery_guide_shown", false)) return
        prefs.edit().putBoolean("battery_guide_shown", true).apply()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(this, BatteryGuideActivity::class.java))
        }
    }
}
