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

    fun String.hexToBytes(): ByteArray =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()

    fun byteArrayOfInts(vararg ints: Int): ByteArray =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }

    fun byteArrayOfIntArray(intArray: IntArray): ByteArray =
        ByteArray(intArray.size) { pos -> intArray[pos].toByte() }

    private fun toByteArray(string: String): ByteArray =
        string.toByteArray(Charsets.UTF_8)

    fun fromByteArrayToHexStr(byteArray: ByteArray): String =
        byteArray.joinToString(separator = "") { String.format("%02X", it) }

    fun fromByteArrayToHexStrWithSpaces(byteArray: ByteArray): String =
        "0x" + byteArray.joinToString(separator = " ") { String.format("%02X", it) }

    fun toByteArray(string: String, maxLen: Int): ByteArray =
        string.toByteArray(Charsets.UTF_8).copyOf(maxLen)

    fun toHexStr(asciiStr: String): String =
        toByteArray(asciiStr).joinToString(separator = "") { "%02x".format(it) }

    fun byteArray(vararg bytes: Byte): ByteArray =
        ByteArray(bytes.size) { pos -> bytes[pos] }

    fun toIntArray(hexStr: String): ArrayList<Int> =
        (if (hexStr.contains(' ')) hexStr.split(' ') else hexStr.chunked(2))
            .map { Integer.parseInt(it.removePrefix("0x"), 16) }
            .toCollection(ArrayList())

    fun toAsciiString(hexStr: String, commandLengthToSkip: Int): String =
        (if (hexStr.contains(' ')) hexStr.split(' ') else hexStr.chunked(2))
            .drop(commandLengthToSkip)
            .filter { it != "00" }
            .joinToString("") {
                Integer.parseInt(it.removePrefix("0x"), 16).toChar().toString()
            }

    fun toCompactString(hexStr: String): String =
        hexStr.split(' ').joinToString(separator = "") {
            if (it.startsWith("0x")) it.removePrefix("0x") else it
        }

    // JSON safe extension functions
    fun JSONObject.getStringSafe(name: String): String? =
        if (!has(name)) null else getString(name)

    fun JSONObject.getBooleanSafe(name: String): Boolean? =
        if (!has(name)) null else getBoolean(name)

    fun JSONObject.getJSONObjectSafe(name: String): JSONObject? =
        if (!has(name)) null else getJSONObject(name)

    fun JSONObject.getJSONArraySafe(name: String): JSONArray? =
        if (!has(name)) null else getJSONArray(name)

    fun JSONObject.getIntSafe(name: String): Int? =
        if (!has(name)) null else getInt(name)

    fun trimNonAsciiCharacters(string: String): String =
        Regex("[^\\x00-\\x7F]").replace(string, "")
}
