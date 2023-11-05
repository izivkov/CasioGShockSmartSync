/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-16, 8:56 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-16, 8:56 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber

class WatchImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        startListener()
        setImageResource()
    }

    private fun startListener() {

        val eventActions = arrayOf(
            EventAction("DeviceName") {
                setImageResource()
            },
        )

        ProgressEvents.subscriber.runEventActions(this.javaClass.canonicalName, eventActions)
    }

    private fun setImageResource() {
        when (WatchInfo.model) {
            WatchInfo.WATCH_MODEL.GA -> {
                setImageResource(R.drawable.ga_b2100)
            }

            WatchInfo.WATCH_MODEL.GW -> {
                // setImageResource(R.drawable.dw_b5600)
                setImageResource(R.drawable.ic_gw_b5600)
            }

            WatchInfo.WATCH_MODEL.DW -> {
                setImageResource(R.drawable.dw_b5600)
            }

            else -> {
                setImageResource(R.drawable.ic_gw_b5600)
            }
        }
    }
}