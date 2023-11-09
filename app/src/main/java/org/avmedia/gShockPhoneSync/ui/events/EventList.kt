/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 9:42 p.m.
 */
package org.avmedia.gShockPhoneSync.ui.events

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class EventList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    object Cache {
        var adapter: EventAdapter? = null
    }

    init {
        adapter = Cache.adapter ?: EventAdapter(EventsModel.events).also { Cache.adapter = it }
        layoutManager = LinearLayoutManager(context)

        listenForUpdateRequest()
        waitForPermissions()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun waitForPermissions() {
        val eventActions = arrayOf(
            EventAction("CalendarPermissionsGranted") {
                if (EventsModel.events.isEmpty()) {
                    EventsModel.refresh(context)
                    (context as Activity).runOnUiThread {
                        adapter?.notifyDataSetChanged()
                    }
                }
            },
        )

        ProgressEvents.runEventActions(this.javaClass.name, eventActions)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun listenForUpdateRequest() {
        val eventActions = arrayOf(
            EventAction("CalendarUpdated") {
                Timber.d("CalendarUpdated, events: ${EventsModel.events.size}")
                EventsModel.refresh(context)
                (context as Activity).runOnUiThread {
                    adapter?.notifyDataSetChanged()
                }
            },
        )

        ProgressEvents.runEventActions(
            this.javaClass.name + "listenForUpdateRequest",
            eventActions
        )
    }
}
