/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-14, 1:48 p.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext

object LocalDataStorage {

    private const val STORAGE_NAME = "CASIO_GOOGLE_SYNC_STORAGE"
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // load in memory, so can perform sync operation
        scope.launch {
            applicationContext().dataStore.data.first()
        }
    }

    private val Context.sharedPreferences
        get() = getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)

    fun put(key: String, value: String) {
        scope.launch {
            applicationContext().dataStore.edit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
    }

    fun get(key: String, defaultValue: String? = null): String? {
        var value: String?
        runBlocking {
            val preferences = applicationContext().dataStore.data.first()
            value = preferences[stringPreferencesKey(key)] ?: defaultValue
        }
        return value
    }

    fun delete(key: String) {
        scope.launch {
            deleteAsync(key)
        }
    }

    suspend fun deleteAsync(key: String) {
        applicationContext().dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
        }
    }

    private fun getBoolean(key: String): Boolean {
        return get(key, "false")?.toBoolean() ?: false
    }

    private fun putBoolean(key: String, value: Boolean) {
        scope.launch {
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

    // Migration related
    private val Context.dataStore by preferencesDataStore(name = STORAGE_NAME,
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, STORAGE_NAME))
        })

    fun migrateSharedPreferencesToDataStore(context: Context) {
        val sharedPrefs = context.sharedPreferences
        val editor = sharedPrefs.edit()
        val data = sharedPrefs.all

        runBlocking {
            context.dataStore.edit { preferences ->
                for ((key, value) in data) {
                    preferences[stringPreferencesKey(key)] = value.toString()
                }
            }
        }
        editor.clear().apply() // Clear SharedPreferences after migration
    }
}