/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gshockapi

import com.google.gson.Gson

object AlarmsModel {


    open class Alarm(var hour: Int, var minute: Int, var enabled: Boolean, var hasHourlyChime: Boolean = false)
    val alarms = ArrayList<Alarm>()

    fun clear() {
        alarms.clear()
    }

    fun isEmpty(): Boolean {
        return alarms.size == 0
    }

    @Synchronized
    fun fromJson(jsonStr: String) {
        val gson = Gson()
        val alarmArr = gson.fromJson(jsonStr, Array<Alarm>::class.java)
        alarms.addAll(alarmArr)
    }

    @Synchronized
    fun toJson(): String {
        val gson = Gson()
        return gson.toJson(alarms)
    }
}