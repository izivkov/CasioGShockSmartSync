/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-16, 8:56 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-16, 8:56 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class WatchImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        startListener()

        val lastModelUsed = LocalDataStorage.get("LastDeviceConnected", "CASIO GW-B5600", context)
        if (lastModelUsed?.contains("2100") == true) {
            setImageResource(R.drawable.ga_b2100)
        } else {
            setImageResource(R.drawable.ic_gw_b5600)
        }
    }

    private fun startListener() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName, {
            when (it) {
                ProgressEvents["DeviceName"] -> {
                    val deviceName = ProgressEvents["DeviceName"]?.payload
                    if (api().getModel() == GShockAPI.WATCH_MODEL.B2100) {
                        setImageResource(R.drawable.ga_b2100)
                    } else {
                        setImageResource(R.drawable.ic_gw_b5600)
                    }
                }
            }
        }, { throwable ->
            Timber.d("Got error on subscribe: $throwable")
            throwable.printStackTrace()
        })
    }
}