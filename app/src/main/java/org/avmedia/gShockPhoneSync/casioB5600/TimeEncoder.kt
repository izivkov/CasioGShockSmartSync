/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 11:01 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 11:01 a.m.
 */

package org.avmedia.gShockPhoneSync.casioB5600

import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

object TimeEncoder {
    fun prepareCurrentTime(date: Date): ByteArray {
        val arr = ByteArray(10)
        val cal = Calendar.getInstance()
        cal.time = date
        val year = cal[Calendar.YEAR]
        arr[0] = (year ushr 0 and 0xff).toByte()
        arr[1] = (year ushr 8 and 0xff).toByte()
        arr[2] = (1 + cal[Calendar.MONTH]).toByte()
        arr[3] = cal[Calendar.DAY_OF_MONTH].toByte()
        arr[4] = cal[Calendar.HOUR_OF_DAY].toByte()
        arr[5] = cal[Calendar.MINUTE].toByte()
        arr[6] = (1 + cal[Calendar.SECOND]).toByte()
        var dayOfWk = (cal[Calendar.DAY_OF_WEEK] - 1).toByte()
        if (dayOfWk.toInt() == 0) dayOfWk = 7
        arr[7] = dayOfWk
        arr[8] = TimeUnit.MILLISECONDS.toSeconds((256 * cal[Calendar.MILLISECOND]).toLong())
            .toInt().toByte()
        arr[9] = 1 // or 0?
        return arr
    }
}
