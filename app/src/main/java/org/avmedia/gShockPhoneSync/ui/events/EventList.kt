/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 9:42 p.m.
 */
package org.avmedia.gShockPhoneSync.ui.events

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class EventList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        adapter = EventAdapter(EventsModel.events)
        layoutManager = LinearLayoutManager(context)

        listenForUpdateRequest()
        waitForPermissions()
    }

    private fun waitForPermissions() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName+"waitForPermissions",

            {
                when (it) {
                    ProgressEvents["CalendarPermissionsGranted"] -> {
                        EventsModel.refresh(context)
                        (context as Activity).runOnUiThread {
                            adapter?.notifyDataSetChanged()
                        }
                    }
                }
            }, { throwable ->
                Timber.d("Got error on subscribe: $throwable")
                throwable.printStackTrace()
            })
    }

    private fun listenForUpdateRequest() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName + "listenForUpdateRequest", {
            when (it) {
                // Somebody has made a change to the model...need to update the UI
                ProgressEvents["CalendarUpdated"] -> {
                    EventsModel.refresh(context)
                    (context as Activity).runOnUiThread {
                        adapter?.notifyDataSetChanged()
                    }
                }
            }
        }, { throwable ->
            Timber.d("Got error on subscribe: $throwable")
            throwable.printStackTrace()
        })
    }
}
