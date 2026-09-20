package com.example.notice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.notice.databinding.ActivityEditAlarmBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class EditAlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "id"
        fun create(context: Context): Intent = Intent(context, EditAlarmActivity::class.java)
        fun edit(context: Context, id: Long): Intent = create(context).putExtra(EXTRA_ID, id)
    }

    private lateinit var binding: ActivityEditAlarmBinding
    private var alarmId: Long = -1L
    private var dayChips: Map<Int, Chip> = emptyMap()

    private var ringtoneUri: String = ""
    private var ringtoneName: String = ""

    private val systemRingtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                @Suppress("DEPRECATION")
                val uri: Uri? =
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (uri == null) {
                    // "Silent" selected in the system picker
                    ringtoneUri = Alarm.RINGTONE_SILENT
                    ringtoneName = getString(R.string.ringtone_silent)
                } else {
                    ringtoneUri = uri.toString()
                    ringtoneName = runCatching {
                        RingtoneManager.getRingtone(this, uri)?.getTitle(this)
                    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
                }
                updateRingtoneRow()
            }
        }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            ringtoneUri = uri.toString()
            ringtoneName = queryDisplayName(uri)
            updateRingtoneRow()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        alarmId = intent.getLongExtra(EXTRA_ID, -1L)
        val existing = if (alarmId >= 0) AlarmStore.get(this, alarmId) else null
        supportActionBar?.title = getString(if (existing != null) R.string.edit_alarm else R.string.add_alarm)
        binding.btnDelete.visibility = if (existing != null) View.VISIBLE else View.GONE

        val now = Calendar.getInstance()
        binding.timePicker.hour = existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)
        binding.timePicker.minute = existing?.minute ?: now.get(Calendar.MINUTE)
        binding.timePicker.setIs24HourView(DateFormat.is24HourFormat(this))
        binding.etLabel.setText(existing?.label.orEmpty())
        binding.etInterval.setText((existing?.intervalMinutes ?: 5).toString())
        ringtoneUri = existing?.ringtoneUri.orEmpty()
        ringtoneName = existing?.ringtoneName.orEmpty()
        updateRingtoneRow()

        dayChips = mapOf(
            Calendar.SUNDAY to binding.chipSun,
            Calendar.MONDAY to binding.chipMon,
            Calendar.TUESDAY to binding.chipTue,
            Calendar.WEDNESDAY to binding.chipWed,
            Calendar.THURSDAY to binding.chipThu,
            Calendar.FRIDAY to binding.chipFri,
            Calendar.SATURDAY to binding.chipSat
        )
        dayChips.forEach { (day, chip) -> chip.isChecked = existing?.days?.contains(day) == true }

        binding.chipQuick.setOnCheckedStateChangeListener { _, checkedIds ->
            checkedIds.firstOrNull()?.let { id ->
                val value = when (id) {
                    R.id.chipQ1 -> "1"
                    R.id.chipQ3 -> "3"
                    R.id.chipQ5 -> "5"
                    R.id.chipQ10 -> "10"
                    R.id.chipQ15 -> "15"
                    R.id.chipQ30 -> "30"
                    else -> return@setOnCheckedStateChangeListener
                }
                binding.etInterval.setText(value)
            }
        }

        binding.rowRingtone.setOnClickListener { showRingtoneDialog() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun showRingtoneDialog() {
        val items = arrayOf(
            getString(R.string.ringtone_pick_system),
            getString(R.string.ringtone_pick_file),
            getString(R.string.ringtone_pick_silent)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ringtone_picker_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> systemRingtonePicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_ALARM
                            )
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TITLE,
                                getString(R.string.ringtone_picker_title)
                            )
                            val current = ringtoneUri
                                .takeIf { it.isNotBlank() && it != Alarm.RINGTONE_SILENT }
                                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                        }
                    )
                    1 -> filePicker.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "audio/*"
                            addFlags(
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    )
                    2 -> {
                        ringtoneUri = Alarm.RINGTONE_SILENT
                        ringtoneName = getString(R.string.ringtone_silent)
                        updateRingtoneRow()
                    }
                }
            }
            .show()
    }

    private fun updateRingtoneRow() {
        binding.tvRingtone.text = when {
            ringtoneUri == Alarm.RINGTONE_SILENT -> getString(R.string.ringtone_silent)
            ringtoneUri.isBlank() -> getString(R.string.ringtone_default)
            ringtoneName.isNotBlank() -> ringtoneName
            else -> ringtoneUri
        }
    }

    private fun queryDisplayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        uri.lastPathSegment.orEmpty()
    }.getOrDefault(uri.lastPathSegment.orEmpty())

    private fun save() {
        val isNew = alarmId < 0
        val alarm = if (!isNew) {
            AlarmStore.get(this, alarmId) ?: return finish()
        } else {
            Alarm(System.currentTimeMillis(), binding.timePicker.hour, binding.timePicker.minute)
        }
        alarm.hour = binding.timePicker.hour
        alarm.minute = binding.timePicker.minute
        alarm.label = binding.etLabel.text?.toString()?.trim().orEmpty()
        alarm.intervalMinutes =
            (binding.etInterval.text?.toString()?.toIntOrNull() ?: 5).coerceIn(1, 720)
        alarm.days = dayChips.filterValues { it.isChecked }.keys.toMutableSet()
        alarm.ringtoneUri = ringtoneUri
        alarm.ringtoneName = ringtoneName
        if (isNew) alarm.enabled = true

        AlarmStore.save(this, alarm)
        if (alarm.enabled) AlarmScheduler.schedule(this, alarm)
        else AlarmScheduler.cancel(this, alarm.id)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_msg)
            .setPositiveButton(R.string.confirm) { _, _ ->
                AlarmStore.delete(this, alarmId)
                AlarmScheduler.cancel(this, alarmId)
                AlarmScheduler.cancelReRing(this, alarmId)
                if (AlarmStore.activeAlarmId(this) == alarmId) {
                    runCatching { RingService.start(this, RingService.ACTION_DISMISS, alarmId) }
                }
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
