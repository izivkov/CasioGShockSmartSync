/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-21, 11:19 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

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

class ActionAdapter(private val actions: ArrayList<IAction>) :
    RecyclerView.Adapter<ActionAdapter.ViewHolder>() {

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Your holder should contain and initialize a member variable
        // for any view that will be set as you render a row

        val title: TextView = itemView.findViewById<TextView>(R.id.title)
        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val actionView = inflater.inflate(R.layout.action_item, parent, false)

        return ViewHolder(actionView)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        Timber.i("onBindViewHolder called...actions.size: ${actions.size}")
        val action: IAction = actions[position]
        val title = viewHolder.title
        try {
            viewHolder.actionEnabled.isChecked = action.enabled
            viewHolder.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
                action.enabled = isChecked
            })

            viewHolder.title.text = action.title
            (viewHolder.itemView as ActionItem).setActionData(action)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int {
        return actions.size
    }
}