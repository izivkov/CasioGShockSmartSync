/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-14, 1:48 p.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext

private const val STORAGE_NAME = "CASIO_GOOGLE_SYNC_STORAGE"
private val Context.dataStore by preferencesDataStore(STORAGE_NAME)

object LocalDataStorage {

    fun put(key: String, value: String, context: Context? = null) {
        MainActivity.getLifecycleScope().launch {
            applicationContext().dataStore.edit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
    }

    fun get(key: String, defaultValue: String? = null): String? {
        var value: String?
        runBlocking {
            val preferences = applicationContext().dataStore.data.first()
            value = (preferences as Preferences)[stringPreferencesKey(key)] ?: defaultValue
        }

        return value
    }

    fun delete(key: String) {
        MainActivity.getLifecycleScope().launch {
            applicationContext().dataStore.edit { preferences ->
                preferences.remove(stringPreferencesKey(key))
            }
        }
    }

    private fun getBoolean(key: String): Boolean {
        var booleanVal = false
        runBlocking {
            booleanVal = get(key, "false")?.toBoolean() ?: false
        }
        return booleanVal
    }

    private fun putBoolean(key: String, value: Boolean) {
        MainActivity.getLifecycleScope().launch {
            put(key, value.toString())
        }
    }

    fun getTimeAdjustmentNotification(): Boolean {
        return getBoolean("timeAdjustmentNotification")
    }

    fun setTimeAdjustmentNotification(value: Boolean) {
        putBoolean("timeAdjustmentNotification", value)
    }

    fun getAllData(): Flow<String> {
        return applicationContext().dataStore.data.map { preferences ->
            val allEntries = preferences.asMap()
            val stringBuilder = StringBuilder()
            allEntries.forEach { (key, value) ->
                stringBuilder.append("$key: $value\n")
            }
            stringBuilder.toString()
        }
    }
}