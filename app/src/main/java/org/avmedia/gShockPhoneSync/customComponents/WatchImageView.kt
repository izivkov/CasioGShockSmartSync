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

class WatchImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        startListener()
    }

    private fun startListener() {

        val eventActions = arrayOf(
            EventAction("DeviceName") {
                setImageResource(ProgressEvents.getPayload("DeviceName") as String)
            },
        )

        ProgressEvents.runEventActions(this.javaClass.name, eventActions)
    }

    private fun setImageResource(deviceName: String) {
        when {
            "GA" in deviceName -> {
                setImageResource(R.drawable.ga_b2100)
            }

            "GMA" in deviceName -> {
                setImageResource(R.drawable.ga_b2100)
            }

            "GW" in deviceName -> {
                setImageResource(R.drawable.ic_gw_b5600)
            }

            "GMW" in deviceName -> {
                setImageResource(R.drawable.ic_gw_b5600)
            }

            "DW" in deviceName -> {
                setImageResource(R.drawable.dw_b5600)
            }

            "DMW" in deviceName -> {
                setImageResource(R.drawable.dw_b5600)
            }

            else -> {
                setImageResource(R.drawable.ic_gw_b5600)
            }
        }
    }
}