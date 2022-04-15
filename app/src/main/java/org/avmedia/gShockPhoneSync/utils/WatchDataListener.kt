/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 10:29 a.m.
 */

package org.avmedia.gShockPhoneSync.utils

import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ble.IDataReceived
import org.avmedia.gShockPhoneSync.casioB5600.AlarmEncoder
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport

/*
This class listens for status data from the Bot and emits events.
These events are received by various custom components which update their UI accordingly.
For example, a right indicator will start blinking if the status on the bot is set.
 */
object WatchDataListener {

    fun init() {
        val dataReceived: IDataReceived = object : IDataReceived {
            override fun dataReceived(command: String?) {
                if (command == null) {
                    return
                }
                val dataJson = CasioSupport.toJson(command)

                for (key in dataJson.keys()) {
                    val value: String = dataJson.getString(key)

                    /*
                    Send an event on a particular subject.
                    The custom components are listening on their subject.
                    */
                    WatchDataEvents.emitEvent(key, value)
                }
            }
        }

        Connection.setDataCallback(dataReceived)
    }
}