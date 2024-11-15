package org.avmedia.gShockSmartSyncCompose.utils

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object LocalDataStorage {

    private const val STORAGE_NAME = "CASIO_GOOGLE_SYNC_STORAGE"
    private val scope = CoroutineScope(Dispatchers.IO)

    // Use a DataStore delegate in context
    private val Context.dataStore by preferencesDataStore(
        name = STORAGE_NAME,
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, STORAGE_NAME))
        }
    )

    fun put(context: Context, key: String, value: String) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
    }

    fun get(context: Context, key: String, defaultValue: String? = null): String? {
        var value: String?
        runBlocking {
            val preferences = context.dataStore.data.first()
            value = preferences[stringPreferencesKey(key)] ?: defaultValue
        }
        return value
    }

    fun delete(context: Context, key: String) {
        scope.launch {
            deleteAsync(context, key)
        }
    }

    suspend fun deleteAsync(context: Context, key: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
        }
    }

    private fun getBoolean(context: Context, key: String): Boolean {
        return get(context, key, "false")?.toBoolean() ?: false
    }

    private fun putBoolean(context: Context, key: String, value: Boolean) {
        scope.launch {
            put(context, key, value.toString())
        }
    }

    fun getTimeAdjustmentNotification(context: Context): Boolean {
        return getBoolean(context, "timeAdjustmentNotification")
    }

    fun setTimeAdjustmentNotification(context: Context, value: Boolean) {
        putBoolean(context, "timeAdjustmentNotification", value)
    }

    fun getFineTimeAdjustment(context: Context): Int {
        return get(context, "fineTimeAdjustment", "0")?.toInt() ?: 0
    }

    fun setFineTimeAdjustment(context: Context, fineTimeAdjustment: Int) {
        return put(context, "fineTimeAdjustment", fineTimeAdjustment.toString())
    }
}
