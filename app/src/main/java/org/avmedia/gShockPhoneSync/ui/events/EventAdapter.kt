/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-21, 11:19 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.events

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.Event
import java.text.ParseException

class EventAdapter(private val events: ArrayList<Event>) :
    RecyclerView.Adapter<EventAdapter.ViewHolder>() {

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Your holder should contain and initialize a member variable
        // for any view that will be set as you render a row
        val titleView: TextView = itemView.findViewById(R.id.title)
        val frequencyView: TextView = itemView.findViewById(R.id.frequency)
        val periodView: TextView = itemView.findViewById(R.id.period)
        val enabledView: SwitchMaterial = itemView.findViewById(R.id.enabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val eventView = inflater.inflate(R.layout.event_item, parent, false)
        return ViewHolder(eventView)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val event: Event = events[position]
        try {
            viewHolder.titleView.text = event.title
            if (event.incompatible) {
                viewHolder.periodView.text = "Event not compatible with Watch"
                viewHolder.frequencyView.text = ""
                viewHolder.enabledView.isChecked = false
            } else {
                viewHolder.periodView.text = event.getPeriodFormatted()
                viewHolder.frequencyView.text = event.getFrequencyFormatted()
                viewHolder.enabledView.isChecked = event.enabled
            }

            viewHolder.enabledView.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked && EventsModel.getEnabledCount() == EventsModel.MAX_REMINDERS) {
                    Utils.snackBar(
                        buttonView.context,
                        "Max ${EventsModel.MAX_REMINDERS} items already enabled. Please disable some first."
                    )
                    viewHolder.enabledView.isChecked = false
                    event.enabled = false
                } else {
                    event.enabled = isChecked
                }
            }

            (viewHolder.itemView as EventItem).setEventData(event)
            (viewHolder.itemView as EventItem).setOnDataChange(::notifyDataSetChanged)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int {
        return events.size
    }
}