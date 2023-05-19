/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-14, 1:48 p.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.content.Context
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gshockapi.WatchInfo
import java.util.*
import kotlin.time.ExperimentalTime

object LocalDataStorage {

    private const val STORAGE_NAME = "CASIO_GOOGLE_SYNC_STORAGE"

    @OptIn(ExperimentalTime::class)
    fun putForDevice(key: String, value: String, context: Context) {
        put("$key.${WatchInfo.model}", value, context)
    }
    fun put(key: String, value: String, context: Context) {
        val sharedPreference = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        var editor = sharedPreference.edit()
        editor.putString("$key", value)
        editor.apply()
    }

    fun getForDevice(key: String, defaultValue: String?, context: Context): String? {
        return get("$key.${WatchInfo.model}", defaultValue, context)
    }

    fun get(key: String, defaultValue: String? = null, context: Context): String? {
        val sharedPreference = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        return sharedPreference.getString(key, null) ?: defaultValue
    }

    fun deleteForDevice(key: String, context: Context) {
        delete("$key.${WatchInfo.model}", context)
    }

    fun delete(key: String, context: Context) {
        val sharedPreference = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        var editor = sharedPreference.edit()
        editor.remove(key)
        editor.apply()
    }

    fun clear(context: Context) {
        val sharedPreference = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        var editor = sharedPreference.edit()
        editor.clear()
        editor.apply()
    }

    fun getTimezoneForFutureUse(context: Context): String {
        var timeZone = ""
        val timeZoneChanged: Boolean =
            getForDevice("timezone", "", context) != TimeZone.getDefault().id
        if (timeZoneChanged) {
            timeZone = TimeZone.getDefault().id
            putForDevice("timezone", TimeZone.getDefault().id, context)
        }

        return timeZone
    }

    private fun getBoolean(key: String): Boolean {
        return getForDevice(key, "false", applicationContext()) == "true"
    }

    private fun putBoolean(key: String, value: Boolean) {
        putForDevice(key, value.toString(), applicationContext())
    }

    fun getTimeAdjustmentNotification(): Boolean {
        return getBoolean("timeAdjustmentNotification")
    }

    fun setTimeAdjustmentNotification(value: Boolean) {
        putBoolean("timeAdjustmentNotification", value)
    }
}