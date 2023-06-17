/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-22, 1:55 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.avmedia.gShockPhoneSync.customComponents.Button
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import timber.log.Timber

class ForgetButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    init {
        setOnTouchListener(OnTouchListener())
        listenForConnection()
    }

    private fun listenForConnection() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName,

            {
                when (it) {
                    ProgressEvents["ConnectionStarted"] -> {
                        isEnabled = false
                        Timber.d("... Do not interrupt...")
                    }

                    ProgressEvents["WatchInitializationCompleted"], ProgressEvents["ConnectionFailed"], ProgressEvents["Disconnect"] -> {
                        isEnabled = true
                        Timber.d("... Can be interrupted...")
                    }
                }
            }, { throwable -> Timber.d("Got error on subscribe: $throwable") })
    }

    inner class OnTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_UP -> {
                    WatchInfo.reset()

                    LocalDataStorage.delete("LastDeviceAddress", context)
                    LocalDataStorage.delete("LastDeviceName", context)

                    Connection.breakWait()
                    ProgressEvents.onNext("DeviceName", "")
                    ProgressEvents.onNext("WaitForConnection")
                }
            }
            v?.performClick()
            return false
        }
    }
}
