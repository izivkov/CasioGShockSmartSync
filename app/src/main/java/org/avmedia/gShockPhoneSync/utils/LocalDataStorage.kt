/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-14, 1:48 p.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.content.Context
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import java.util.*
import kotlin.time.ExperimentalTime

object LocalDataStorage {

    private const val STORAGE_NAME = "CASIO_GOOGLE_SYNC_STORAGE"

    @OptIn(ExperimentalTime::class)
    fun put(key: String, value: String, context: Context) {
        val sharedPreference = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        var editor = sharedPreference.edit()
        editor.putString("$key", value)
        editor.apply()
    }

    fun get(key: String, defaultValue: String? = null, context: Context): String? {
        val sharedPreference = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
        return sharedPreference.getString(key, null) ?: defaultValue
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
            get("timezone", "", context) != TimeZone.getDefault().id
        if (timeZoneChanged) {
            timeZone = TimeZone.getDefault().id
            put("timezone", TimeZone.getDefault().id, context)
        }

        return timeZone
    }

    private fun getBoolean(key: String): Boolean {
        return get(key, "false", applicationContext()) == "true"
    }

    private fun putBoolean(key: String, value: Boolean) {
        put(key, value.toString(), applicationContext())
    }

    fun getTimeAdjustmentNotification(): Boolean {
        return getBoolean("timeAdjustmentNotification")
    }

    fun setTimeAdjustmentNotification(value: Boolean) {
        putBoolean("timeAdjustmentNotification", value)
    }
}