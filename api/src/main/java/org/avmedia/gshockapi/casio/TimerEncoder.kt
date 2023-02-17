/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 11:01 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 11:01 a.m.
 */

package org.avmedia.gshockapi.casio

/*
Send:
18 05 06 07 00 00 00 00

Send:
Handle: 0xe
6:7:8
18 06 07 08 00 00 00 00
 */

object TimerEncoder {
    fun encode(secondsStr: String): ByteArray {
        val inSeconds = secondsStr.toInt()
        val hours = inSeconds / 3600
        val minutesAndSeconds = inSeconds % 3600
        val minutes = minutesAndSeconds / 60
        val seconds = minutesAndSeconds % 60

        val arr = ByteArray(7)
        arr[0] = 0x18
        arr[1] = hours.toByte()
        arr[2] = minutes.toByte()
        arr[3] = seconds.toByte()

        return arr
    }
}
