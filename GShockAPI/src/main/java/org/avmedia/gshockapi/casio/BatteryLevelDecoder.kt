/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-16, 6:46 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-16, 6:46 p.m.
 */

package org.avmedia.gshockapi.casio

import org.avmedia.gshockapi.utils.Utils

object BatteryLevelDecoder {

    fun decodeValue(data: String): String {
        var percent = 0

        var cmdInts = Utils.toIntArray(data)
        // command looks like 0x28 13 1E 00.
        // 50% level is obtain from the second Int 13:
        // 0x13 = 0b00010011
        // take MSB 0b0001. If it is 1, we have 50% charge
        val MASK_50_PERCENT = 0b00010000
        percent += if (cmdInts[1] or MASK_50_PERCENT != 0) 50 else 0

        // Fine value is obtained from the 3rd integer, 0x1E. The LSB (0xE) represents
        // the fine value between 0 and 0xf, which is the other 50%. So, to
        // get this value, we have 50% * 0xe / 0xf. We add this to the previous battery level.
        // So, for command 0x28 13 1E 00, our battery level would be:
        // 50% (from 0x13) + 47 = 97%
        // The 47 number was obtained from 50 * 0xe / 0xf or 50 * 14/15 = 46.66

        val MASK_FINE_VALUE = 0xf
        val fineValue = cmdInts[2] and MASK_FINE_VALUE
        percent += 50 * fineValue / 15

        return percent.toString()
    }
}