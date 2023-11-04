/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 11:56 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.main

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import org.avmedia.gShockPhoneSync.IHideableLayout
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.DeviceCharacteristics
import org.avmedia.gshockapi.io.CachedIO
import timber.log.Timber

class ConnectionLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), IHideableLayout {

    init {
        show()
        createAppEventsSubscription()
    }

    private fun createAppEventsSubscription() {
        val eventActions = arrayOf(
            EventAction("ButtonPressedInfoReceived") { _ ->
                if (api().isActionButtonPressed()) {
                    hide()
                }
            },
            EventAction("WatchInitializationCompleted") { _ ->
                if (!api().isActionButtonPressed() && !api().isAutoTimeStarted()) {
                    println("connectionLayout: hide")
                    hide()
                }
            },
            EventAction("Disconnect") { _ ->
                println("connectionLayout: show")
                show()
            },
        )

        ProgressEvents.subscriber.runEventActions(this.javaClass.canonicalName, eventActions)
    }

    override fun show() {
        visibility = View.VISIBLE
    }

    override fun hide() {
        visibility = View.GONE
    }
}
