package com.myappstore.alarmclock

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar

/** Alarm list: the app's home screen. */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AlarmAdapter
    private lateinit var emptyView: View
    private lateinit var exactAlarmWarning: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emptyView = findViewById(R.id.emptyView)
        exactAlarmWarning = findViewById(R.id.exactAlarmWarning)

        adapter = AlarmAdapter(
            onToggle = { alarm, enabled ->
                alarm.enabled = enabled
                AlarmStore.save(this, alarm)
                AlarmScheduler.schedule(this, alarm)
                refresh()
            },
            onClick = { alarm -> openEditor(alarm.id) }
        )
        findViewById<RecyclerView>(R.id.alarmList).let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
        }

        findViewById<FloatingActionButton>(R.id.addAlarm).setOnClickListener { openEditor(-1) }
        findViewById<Button>(R.id.grantExactAlarm).setOnClickListener { requestExactAlarmAccess() }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val alarms = AlarmStore.all(this)
        adapter.submit(alarms)
        emptyView.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        exactAlarmWarning.visibility =
            if (AlarmScheduler.canScheduleExact(this)) View.GONE else View.VISIBLE
    }

    private fun openEditor(alarmId: Int) {
        startActivity(
            Intent(this, AlarmEditActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        )
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}

class AlarmAdapter(
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.ViewHolder>() {

    private val items = mutableListOf<Alarm>()

    fun submit(alarms: List<Alarm>) {
        items.clear()
        items.addAll(alarms)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.itemTime)
        val details: TextView = view.findViewById(R.id.itemDetails)
        val toggle: MaterialSwitch = view.findViewById(R.id.itemToggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alarm = items[position]
        val context = holder.itemView.context
        holder.time.text = String.format("%02d:%02d", alarm.hour, alarm.minute)

        val parts = mutableListOf<String>()
        if (alarm.label.isNotBlank()) parts.add(alarm.label)
        parts.add(daysText(context, alarm.days))
        parts.add(context.getString(R.string.duration_short, alarm.ringSeconds / 60, alarm.ringSeconds % 60))
        if (alarm.overrideSystemSound) parts.add(context.getString(R.string.override_short))
        holder.details.text = parts.joinToString(" · ")

        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = alarm.enabled
        holder.toggle.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            onToggle(alarm, checked)
        }
        holder.itemView.setOnClickListener { onClick(alarm) }
    }

    private fun daysText(context: android.content.Context, days: Set<Int>): String {
        if (days.isEmpty()) return context.getString(R.string.once)
        val names = context.resources.getStringArray(R.array.day_names_short)
        val order = listOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )
        return order.filter { days.contains(it) }.joinToString(", ") { names[it - 1] }
    }
}
