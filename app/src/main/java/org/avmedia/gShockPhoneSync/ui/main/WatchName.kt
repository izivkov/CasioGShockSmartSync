/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 5:57 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo

class WatchName @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        createSubscription()

        val deviceName = LocalDataStorage.get("LastDeviceName", "")
        text = if (deviceName.isNullOrBlank()) {
            resources.getString(R.string.no_watch)
        } else {
            deviceName.removePrefix("CASIO").trim()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun createSubscription() {
        val eventActions = arrayOf(
            EventAction("DeviceName") {
                val deviceName = ProgressEvents.getPayload("DeviceName")
                if ((deviceName as String).isBlank()) {
                    text = resources.getString(R.string.no_watch)
                }
                if (deviceName.contains("CASIO")) {
                    text = deviceName.removePrefix("CASIO").trim()
                }
            },
        )

        ProgressEvents.runEventActions(this.javaClass.name, eventActions)
    }
}
