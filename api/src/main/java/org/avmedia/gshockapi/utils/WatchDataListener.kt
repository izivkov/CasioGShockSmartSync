/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 10:29 a.m.
 */

package org.avmedia.gshockapi.utils

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.IDataReceived
import org.avmedia.gshockapi.casio.MessageDispatcher

/*
This class accepts data from the watch and calls dataReceived() method on MessageDispatcher class.
From there, the appropriate onReceived() method is called for the corresponding IO class.
 */
@RequiresApi(Build.VERSION_CODES.O)
object WatchDataListener {
    fun init() {
        val dataReceived: IDataReceived = object : IDataReceived {
            override fun dataReceived(data: String?) {
                data?.let { MessageDispatcher.onReceived(it) }
            }
        }

        fun waitForConnectionSetupComplete() {
            val eventActions = arrayOf(
                EventAction("ConnectionSetupComplete") {
                    Connection.setDataCallback(dataReceived)
                },
            )

            ProgressEvents.subscriber.runEventActions(this.javaClass.name, eventActions)
        }

        waitForConnectionSetupComplete()
    }
}