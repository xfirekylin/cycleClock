package com.example.notice

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.notice.databinding.ItemAlarmBinding
import java.util.Calendar
import java.util.Locale

class AlarmAdapter(
    private val onClick: (Alarm) -> Unit,
    private val onLongClick: (Alarm) -> Unit,
    private val onToggle: (Alarm, Boolean) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.VH>() {

    private val items = mutableListOf<Alarm>()

    fun submit(list: List<Alarm>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemAlarmBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val alarm = items[position]
        val ctx: Context = holder.binding.root.context
        val b = holder.binding

        b.tvTime.text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
        if (alarm.label.isBlank()) {
            b.tvLabel.visibility = View.GONE
        } else {
            b.tvLabel.visibility = View.VISIBLE
            b.tvLabel.text = alarm.label
        }

        val meta = buildString {
            append(alarm.daysDisplay(ctx))
            append(" · ").append(ctx.getString(R.string.meta_interval, alarm.intervalMinutes))
            if (!alarm.enabled) {
                append("\n").append(ctx.getString(R.string.disabled))
            } else {
                val activeId = AlarmStore.activeAlarmId(ctx)
                if (activeId == alarm.id) {
                    append("\n").append(
                        ctx.getString(
                            if (AlarmStore.wasRinging(ctx)) R.string.ringing_now
                            else R.string.waiting
                        )
                    )
                } else {
                    AlarmScheduler.nextTrigger(alarm)?.let {
                        append("\n")
                            .append(ctx.getString(R.string.next_prefix))
                            .append(" ")
                            .append(formatNext(ctx, it))
                    }
                }
            }
        }
        b.tvMeta.text = meta

        b.swEnabled.setOnCheckedChangeListener(null)
        b.swEnabled.isChecked = alarm.enabled
        b.swEnabled.setOnCheckedChangeListener { _, checked -> onToggle(alarm, checked) }
        b.root.setOnClickListener { onClick(alarm) }
        b.root.setOnLongClickListener {
            onLongClick(alarm)
            true
        }
    }

    companion object {
        fun formatNext(ctx: Context, time: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = time }
            val now = Calendar.getInstance()
            val hm = String.format(
                Locale.getDefault(), "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
            )
            val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
            val tomorrow = run {
                val t = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                cal.get(Calendar.YEAR) == t.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
            }
            return when {
                sameDay -> ctx.getString(R.string.today, hm)
                tomorrow -> ctx.getString(R.string.tomorrow, hm)
                else -> String.format(
                    Locale.getDefault(), "%d月%d日 %s",
                    cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), hm
                )
            }
        }
    }
}
