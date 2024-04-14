/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-21, 11:19 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.alarms

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gshockapi.Alarm
import timber.log.Timber
import java.text.ParseException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class AlarmAdapter(private val alarms: ArrayList<Alarm>) :
    RecyclerView.Adapter<AlarmAdapter.ViewHolder>() {

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Your holder should contain and initialize a member variable
        // for any view that will be set as you render a row
        val timeView: TextView = itemView.findViewById(R.id.time)
        val alarmEnabled: SwitchMaterial = itemView.findViewById(R.id.alarmEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val alarmView = inflater.inflate(R.layout.alarm_item, parent, false)

        return ViewHolder(alarmView)
    }

    @SuppressLint("SimpleDateFormat")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Timber.i("onBindViewHolder called...alarms.size: ${alarms.size}")
        val alarm: Alarm = alarms[position]
        val timeView = viewHolder.timeView
        val alarmEnabled = viewHolder.alarmEnabled

        fun formatTime(time: LocalTime, locale: Locale): String {
            val formatter = DateTimeFormatter.ofPattern("h:mm a", locale)
            val formattedTime = formatter.format(time)
            return if (formattedTime.startsWith("0")) {
                "12${formattedTime.substring(2)}"
            } else {
                formattedTime
            }
        }

        // Replaces 0:33 with 12:33
        fun from0to12(formattedTime: String): String {
            return if (formattedTime.startsWith("0")) {
                "12${formattedTime.substring(1)}"
            } else {
                formattedTime
            }
        }

        try {
            val sdf = SimpleDateFormat("H:mm")
            val dateObj: Date = sdf.parse(alarm.hour.toString() + ":" + alarm.minute.toString())

            val timeFormat = if (java.text.SimpleDateFormat()
                    .toPattern().split(" ")[1][0] == 'h'
            ) "K:mm aa" else "H:mm"

            val time = SimpleDateFormat(timeFormat).format(dateObj)
            timeView.text = if (timeFormat.contains("aa")) from0to12(time) else time

            alarmEnabled.isChecked = alarm.enabled
            alarmEnabled.setOnCheckedChangeListener { _, isChecked ->
                alarm.enabled = isChecked
            }

            (viewHolder.itemView as AlarmItem).setAlarmData(alarm)
            (viewHolder.itemView as AlarmItem).setOnDataChange(::notifyDataSetChanged)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int {
        return alarms.size
    }
}