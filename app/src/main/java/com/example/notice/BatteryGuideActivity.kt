package com.example.notice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.notice.databinding.ActivityBatteryGuideBinding

/**
 * Guides the user through exempting the app from battery optimization (Doze),
 * so exact alarms and the ringing foreground service survive deep sleep.
 * Shown once on first launch (when not whitelisted) and always reachable
 * from the main screen's menu.
 */
class BatteryGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatteryGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatteryGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRequest.setOnClickListener { requestWhitelist() }
        binding.btnDone.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun isWhitelisted(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun refreshStatus() {
        if (isWhitelisted()) {
            binding.tvWhitelistStatus.text = getString(R.string.battery_status_ok)
            binding.tvWhitelistStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
            binding.btnRequest.text = getString(R.string.battery_guide_request_done)
            binding.btnRequest.isEnabled = false
        } else {
            binding.tvWhitelistStatus.text = getString(R.string.battery_status_bad)
            binding.tvWhitelistStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
            binding.btnRequest.text = getString(R.string.battery_guide_request)
            binding.btnRequest.isEnabled = true
        }
    }

    private fun requestWhitelist() {
        // Preferred: the system "allow <app> to run in background?" yes/no dialog.
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        if (runCatching { startActivity(direct) }.isSuccess) return

        // Fallback 1: the battery-optimization list, user finds the app manually.
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (runCatching { startActivity(list) }.isSuccess) return

        // Fallback 2: the app's details page.
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}
