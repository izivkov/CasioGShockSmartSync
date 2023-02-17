/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 11:01 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 11:01 a.m.
 */

package org.avmedia.gshockapi.casio

import android.annotation.SuppressLint
import java.time.LocalDateTime

object TimeEncoder {
    @SuppressLint("NewApi")
    fun prepareCurrentTime(date: LocalDateTime): ByteArray {
        val arr = ByteArray(10)
        val year = date.year
        arr[0] = (year ushr 0 and 0xff).toByte()
        arr[1] = (year ushr 8 and 0xff).toByte()
        arr[2] = date.month.value.toByte()
        arr[3] = date.dayOfMonth.toByte()
        arr[4] = date.hour.toByte()
        arr[5] = date.minute.toByte()
        arr[6] = date.second.toByte()
        arr[7] = date.dayOfWeek.value.toByte()
        arr[8] = (date.nano / 1000000).toByte()
        arr[9] = 1 // or 0?
        return arr
    }
}
