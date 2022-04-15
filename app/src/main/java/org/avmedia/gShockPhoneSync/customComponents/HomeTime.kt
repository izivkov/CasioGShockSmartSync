/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:24 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import org.jetbrains.anko.runOnUiThread
import timber.log.Timber

class HomeTime @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    object CashedHomeTime {
        var homeCity = ""
        var isSet = false
    }

    init {
        if (!CashedHomeTime.isSet) {
            subscribe("CASIO_WORLD_CITIES", ::onDataReceived)
        }
        text = CashedHomeTime.homeCity

        createAppEventsSubscription()
    }

    private fun createAppEventsSubscription() {
        ProgressEvents.subscriber.start(
            this.javaClass.simpleName,

            {
                when (it) {
                    ProgressEvents.Events.WatchDataCollected -> {
                        CasioSupport.requestHomeTime ()
                    }
                }
            },
            { throwable -> Timber.d("Got error on subscribe: $throwable") })
    }

    private fun subscribe(subject: String, onDataReceived: (String) -> Unit) {
        WatchDataEvents.addSubject(subject)
        WatchDataEvents.subscribe(this.javaClass.simpleName, subject, onNext = {
            onDataReceived(it as String)
        })
    }

    private fun onDataReceived(data: String) {
        // only the first city is the home time location, handle 0x1f, 0x0 only.
        if (data.split(" ")[1].toInt() != 0) {
            return
        }
        CashedHomeTime.homeCity = Utils.toAsciiString(data, 1)
        context.runOnUiThread {
            text = CashedHomeTime.homeCity
        }
        WatchName.CashedName.isSet = true
    }
}
