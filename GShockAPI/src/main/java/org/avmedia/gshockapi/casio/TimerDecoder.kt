/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-03, 10:57 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-03, 10:57 a.m.
 */

package org.avmedia.gshockapi.casio

import org.avmedia.gshockapi.utils.Utils

/*
Send:
18 05 06 07 00 00 00 00

Send:
Handle: 0xe
6:7:8
18 06 07 08 00 00 00 00
 */

object TimerDecoder {

    fun decodeValue(data: String): String {
        val timerIntArray = Utils.toIntArray(data)

        val hours = timerIntArray[1]
        val minutes = timerIntArray[2]
        val seconds = timerIntArray[3]

        val inSeconds = hours * 3600 + minutes * 60 + seconds
        return inSeconds.toString()
    }
}
