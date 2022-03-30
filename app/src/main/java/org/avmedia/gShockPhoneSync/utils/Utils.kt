/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-28, 10:38 a.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.content.Context
import android.widget.Toast
import java.util.Locale


object Utils {
    public fun String.hexToBytes() =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()

    public fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }

    public fun byteArray(vararg bytes: Byte) = ByteArray(bytes.size) { pos -> bytes[pos] }

    public fun toast(context: Context, message: String) {
        val toast: Toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.show()
    }

    fun toIntArray(hexStr: String): ArrayList<Int> {
        val intArr = ArrayList<Int>()
        val strArray = hexStr.split(' ')
        strArray.forEach {
            var s = it
            if (s.startsWith("0x")) {
                s = s.removePrefix("0x")
            }
            intArr.add(Integer.parseInt(s, 16))
        }

        return intArr
    }

    fun toAsciiString(hexStr: String, commandLengthToSkip: Int): String {
        var asciiStr = ""
        val strArrayWithCommand = hexStr.split(' ')

        // skip command
        val strArray = strArrayWithCommand.subList(commandLengthToSkip, strArrayWithCommand.size)
        strArray.forEach {
            var s = it
            if (s.startsWith("0x")) {
                s = s.removePrefix("0x")
            }
            asciiStr += Integer.parseInt(s, 16).toChar()
        }

        return asciiStr
    }

    fun toCompactString(hexStr: String): String {
        var compactString = ""
        val strArray = hexStr.split(' ')
        strArray.forEach {
            var s = it
            if (s.startsWith("0x")) {
                s = s.removePrefix("0x")
            }
            compactString += s
        }

        return compactString
    }
}