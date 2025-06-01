/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-28, 10:38 a.m.
 */

package org.avmedia.gshockapi.utils

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object Utils {

    fun String.hexToBytes() =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()

    fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }

    fun byteArrayOfIntArray(intArray: IntArray) =
        ByteArray(intArray.size) { pos -> intArray[pos].toByte() }

    private fun toByteArray(string: String): ByteArray {
        val charset = Charsets.UTF_8
        return string.toByteArray(charset)
    }

    fun fromByteArrayToHexStr(byteArray: ByteArray): String {
        val result = StringBuilder()
        for (b in byteArray) {
            result.append(String.format("%02X", b))
        }
        return result.toString()
    }

    fun fromByteArrayToHexStrWithSpaces(byteArray: ByteArray): String {
        val result = StringBuilder()
        result.append("0x")
        for (b in byteArray) {
            result.append(String.format("%02X ", b))
        }
        return result.toString()
    }

    fun toByteArray(string: String, maxLen: Int): ByteArray {
        val charset = Charsets.UTF_8
        val retArr = string.toByteArray(charset)
        if (retArr.size > maxLen) {
            return retArr.take(maxLen).toByteArray()
        }
        if (retArr.size < maxLen) {
            return retArr + ByteArray(maxLen - retArr.size)
        }

        return retArr
    }

    fun toHexStr(asciiStr: String): String {
        val byteArr = toByteArray(asciiStr)
        var hexStr = ""
        byteArr.forEach {
            hexStr += "%02x".format(it)
        }
        return hexStr
    }

    fun byteArray(vararg bytes: Byte) = ByteArray(bytes.size) { pos -> bytes[pos] }

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
        var strArrayWithCommand = hexStr.split(' ')
        if (strArrayWithCommand.size == 1) { // no spaces between hex values, i.e. 4C4F4E444F4E
            strArrayWithCommand = hexStr.chunked(2)
        }

        // skip command
        val strArray = strArrayWithCommand.subList(commandLengthToSkip, strArrayWithCommand.size)
        strArray.forEach {
            if (it != "00") {
                var s = it
                if (s.startsWith("0x")) {
                    s = s.removePrefix("0x")
                }
                asciiStr += Integer.parseInt(s, 16).toChar()
            }
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

    // JSON safe functions, prevent throwing exceptions
    fun JSONObject.getStringSafe(name: String): String? {
        if (!has(name)) {
            return null
        }
        return getString(name)
    }

    fun JSONObject.getBooleanSafe(name: String): Boolean? {
        if (!has(name)) {
            return null
        }
        return getBoolean(name)
    }

    fun JSONObject.getJSONObjectSafe(name: String): JSONObject? {
        if (!has(name)) {
            return null
        }
        return getJSONObject(name)
    }

    fun JSONObject.getJSONArraySafe(name: String): JSONArray? {
        if (!has(name)) {
            return null
        }
        return getJSONArray(name)
    }

    fun JSONObject.getIntSafe(name: String): Int? {
        if (!has(name)) {
            return null
        }
        return getInt(name)
    }

    fun trimNonAsciiCharacters(string: String): String {
        val pattern = Regex("[^\\x00-\\x7F]")
        return pattern.replace(string, "")
    }
}