/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-21, 11:19 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.icu.text.SimpleDateFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.avmedia.gShockPhoneSync.R
import timber.log.Timber
import java.text.ParseException
import java.util.Date

class AlarmAdapter(private val alarms: ArrayList<AlarmsModel.Alarm>) :
    RecyclerView.Adapter<AlarmAdapter.ViewHolder>() {

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Your holder should contain and initialize a member variable
        // for any view that will be set as you render a row
        val timeView: TextView = itemView.findViewById<TextView>(R.id.time)
        val alarmEnabled: SwitchMaterial = itemView.findViewById<SwitchMaterial>(R.id.alarmEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val alarmView = inflater.inflate(R.layout.alarm_item, parent, false)

        return ViewHolder(alarmView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Timber.i("onBindViewHolder called...alarms.size: ${alarms.size}")
        val alarm: AlarmsModel.Alarm = alarms[position]
        val timeView = viewHolder.timeView
        val alarmEnabled = viewHolder.alarmEnabled
        try {
            val sdf = SimpleDateFormat("H:mm")
            val dateObj: Date = sdf.parse(alarm.hour.toString() + ":" + alarm.minute.toString())
            val time = SimpleDateFormat("K:mm aa").format(dateObj)
            timeView.text = time
            alarmEnabled.isChecked = alarm.enabled

            alarmEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
                alarm.enabled = isChecked
            })

            (viewHolder.itemView as AlarmItem).setAlarmData(alarm)
            viewHolder.itemView.setOnDataChange(::notifyDataSetChanged)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int {
        return alarms.size
    }
}