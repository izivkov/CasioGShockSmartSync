/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockSmartSyncCompose.ui.alarms

import org.avmedia.gshockapi.Alarm

object AlarmsModel {

    private val alarms = ArrayList<Alarm>()

    fun isEmpty(): Boolean {
        return alarms.size == 0
    }

    fun getAlarms(): ArrayList<Alarm> {
        return alarms
    }

    fun clear() {
        alarms.clear()
    }

    fun addAll(alarms: ArrayList<Alarm>) {
        this.alarms.addAll(alarms)
    }
}