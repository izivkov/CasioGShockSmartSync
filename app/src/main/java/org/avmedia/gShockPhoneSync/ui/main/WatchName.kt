/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 5:57 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber

class WatchName @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        var shortName = WatchInfo.shortName
        if (shortName.isBlank()) {
            shortName = "No Watch"
        }
        text = shortName

        createSubscription()
    }

    @SuppressLint("SetTextI18n")
    private fun createSubscription() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName as String,
            {
                when (it) {
                    ProgressEvents["DeviceName"] -> {
                        val deviceName = ProgressEvents.getPayload("DeviceName")
                        if ((deviceName as String).isBlank()) {
                            text = "No Watch"
                        }
                        if (deviceName.contains("CASIO")) {
                            text = deviceName.removePrefix("CASIO").trim()
                        }
                    }
                }
            }, { throwable ->
                Timber.d("Got error on subscribe: $throwable")
                throwable.printStackTrace()
            })
    }
}
