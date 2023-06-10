/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 2:38 p.m.
 */

package org.avmedia.gshockapi.apiIO

import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.WatchFactory

object CasioIO {

    fun request(request: String) {
        writeCmd(0xC, request)
    }

    fun init() {
        Connection.enableNotifications()
    }

    fun writeCmd(handle: Int, cmd: String) {
        WatchFactory.watch.writeCmdFromString(handle, cmd)
    }
}