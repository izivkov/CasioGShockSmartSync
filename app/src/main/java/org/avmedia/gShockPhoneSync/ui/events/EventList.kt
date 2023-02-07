/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 9:42 p.m.
 */
package org.avmedia.gShockPhoneSync.ui.events

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.gshockapi.utils.ProgressEvents
import org.jetbrains.anko.runOnUiThread
import timber.log.Timber

class EventList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        adapter = EventAdapter(EventsModel.events)
        layoutManager = LinearLayoutManager(context)

        var eventsModel = EventsModel
        eventsModel.clear()
        eventsModel.events.addAll(CalenderEvents.getEventsFromCalendar(context))

        listenForUpdateRequest()
    }

    private fun listenForUpdateRequest(): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                when (it) {
                    // Somebody has made a change to the model...need to update the UI
                    ProgressEvents.Events.CalendarUpdated -> {
                        EventsModel.refresh(context)
                        context.runOnUiThread {
                            adapter?.notifyDataSetChanged()
                        }
                    }
                }
            }
            .subscribe(
                { },
                { throwable -> Timber.i("Got error on subscribe: $throwable") })
}
