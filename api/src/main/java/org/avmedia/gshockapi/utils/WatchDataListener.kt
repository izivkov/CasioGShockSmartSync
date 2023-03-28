/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 10:29 a.m.
 */

package org.avmedia.gshockapi.utils

import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.IDataReceived
import org.avmedia.gshockapi.casio.WatchFactory
import org.json.JSONObject

/*
This class accepts data from the watch and sends it via "emitEvent()" to whatever
component is interested in it. Components would subscribe to receive
data by "topic". The "topic" is the key of the JSON object of the data.
 */
object WatchDataListener {

    fun init() {
        val dataReceived: IDataReceived = object : IDataReceived {
            override fun dataReceived(data: String?) {
                if (data == null) {
                    return
                }
                val dataJson = WatchFactory.watch.toJson(data)

                for (topic in dataJson.keys()) {
                    val value: JSONObject = dataJson.getJSONObject(topic)
                    WatchDataEvents.emitEvent(topic, value)
                }
            }
        }

        Connection.setDataCallback(dataReceived)
    }
}