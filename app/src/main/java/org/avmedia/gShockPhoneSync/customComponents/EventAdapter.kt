/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-21, 11:19 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.utils.Utils
import timber.log.Timber
import java.text.ParseException

class EventAdapter(private val events: ArrayList<EventsData.Event>) :
    RecyclerView.Adapter<EventAdapter.ViewHolder>() {

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Your holder should contain and initialize a member variable
        // for any view that will be set as you render a row
        val titleView: TextView = itemView.findViewById<TextView>(R.id.title)
        val frequencyView: TextView = itemView.findViewById<TextView>(R.id.frequency)
        val periodView: TextView = itemView.findViewById<TextView>(R.id.period)
        val selectedView: MaterialCheckBox = itemView.findViewById<MaterialCheckBox>(R.id.selected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val eventView = inflater.inflate(R.layout.event_item, parent, false)
        return ViewHolder(eventView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Timber.i("onBindViewHolder called...events.size: ${events.size}")
        val event: EventsData.Event = events[position]
        try {
            viewHolder.titleView.text = event.title
            if (event.incompatible) {
                viewHolder.periodView.text = "Incompatible with watch's reminders"
                viewHolder.frequencyView.text = ""
                viewHolder.selectedView.isChecked = false
                viewHolder.selectedView.isEnabled = false
            } else {
                viewHolder.periodView.text = event.getPeriodFormatted()
                viewHolder.frequencyView.text = event.getFrequencyFormatted()
                viewHolder.selectedView.isChecked = event.selected
                viewHolder.selectedView.isEnabled = true
            }

            viewHolder.selectedView.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked && EventsData.getSelectedCount() == EventsData.MAX_REMINDERS) {
                    Utils.toast(buttonView.context, "Max ${EventsData.MAX_REMINDERS} items selected. Please unselect some first.")
                    viewHolder.selectedView.isChecked = false
                    event.selected = false
                } else {
                    event.selected = isChecked
                }
            })

            (viewHolder.itemView as EventItem).setEventData(event)
            viewHolder.itemView.setOnDataChange(::notifyDataSetChanged)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int {
        return events.size
    }
}