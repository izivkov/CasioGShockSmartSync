/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-16, 4:27 p.m.
 */

package org.avmedia.gshockapi.ble

import android.content.Context

interface IConnection {

    fun init(context: Context)
    fun setDataCallback(dataCallback: IDataReceived?)
    fun connect(context: Context)
    fun disconnect(context: Context? = null)
    fun isConnected(): Boolean
    fun isConnecting(): Boolean
    fun sendMessage(message: String)
    fun start()
    fun stop()
    fun getDeviceId(): String
}