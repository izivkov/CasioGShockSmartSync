/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-28, 10:38 a.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Color.BLACK
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.internal.ContextUtils.getActivity
import com.google.android.material.snackbar.Snackbar
import org.avmedia.gShockPhoneSync.R
import org.jetbrains.anko.contentView
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

object Utils {

    fun isDebugMode(): Boolean {
        return false
    }

    fun String.hexToBytes() =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()

    fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }

    fun byteArrayOfIntArray(intArray: IntArray) =
        ByteArray(intArray.size) { pos -> intArray[pos].toByte() }

    fun toByteArray(string: String): ByteArray {
        val charset = Charsets.UTF_8
        return string.toByteArray(charset)
    }

    fun toByteArray(string: String, maxLen: Int): ByteArray {
        val charset = Charsets.UTF_8
        var retArr = string.toByteArray(charset)
        if (retArr.size > maxLen) {
            return retArr.take(maxLen).toByteArray()
        }
        if (retArr.size < maxLen) {
            return retArr + ByteArray(maxLen - retArr.size)
        }

        return retArr
    }

    fun toHexStr(asciiStr: String): String {
        var byteArr = toByteArray(asciiStr)
        var hexStr = ""
        byteArr.forEach {
            // hexStr += it.toString(16)
            hexStr += "%02x".format(it)
        }
        return hexStr
    }

    fun byteArray(vararg bytes: Byte) = ByteArray(bytes.size) { pos -> bytes[pos] }

    fun toast(context: Context, message: String) {
        val toast: Toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.show()
    }

    fun snackBar(view: View, message: String) {

        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setActionTextColor(Color.BLUE)
            .setBackgroundTint(ContextCompat.getColor(view.context, R.color.grey_700))
            .setTextColor(Color.WHITE)
            .show()
    }

    fun snackBar(context: Context, message: String) {
        snackBar((context as Activity).contentView!!, message)
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
        var strArrayWithCommand = hexStr.split(' ')
        if (strArrayWithCommand.size == 1) { // no spaces between hex values, i.e. 4C4F4E444F4E
            strArrayWithCommand = hexStr.chunked(2)
        }

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

    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            for (service in manager!!.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}