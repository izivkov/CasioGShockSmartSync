package org.avmedia.gshockGoogleSync.utils

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

object LocalDataStorage {

    private const val STORAGE_NAME = "CASIO_GOOGLE_SYNC_STORAGE"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()

    // Use a DataStore delegate in context
    private val Context.dataStore by preferencesDataStore(
        name = STORAGE_NAME,
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, STORAGE_NAME))
        }
    )

    fun put(context: Context, key: String, value: String) {
        scope.launch {
            mutex.withLock {
                context.dataStore.edit { preferences ->
                    preferences[stringPreferencesKey(key)] = value
                }
            }
        }
    }

    fun get(context: Context, key: String, defaultValue: String? = null): String? {
        var value: String?
        runBlocking {
            mutex.withLock {
                val preferences = context.dataStore.data.first()
                value = preferences[stringPreferencesKey(key)] ?: defaultValue
            }
        }
        return value
    }

    fun delete(context: Context, key: String) {
        scope.launch {
            mutex.withLock {
                deleteAsync(context, key)
            }
        }
    }

    suspend fun deleteAsync(context: Context, key: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
        }
    }

    /**
     * Reads all key-value pairs from the DataStore and returns them as a JSONObject.
     * This is useful for backups or exporting settings.
     *
     * @param context The application context.
     * @return A JSONObject containing all stored preferences.
     */
    fun toJsonObject(context: Context): JSONObject {
        val jsonObject = JSONObject()
        runBlocking {
            mutex.withLock {
                val preferences = context.dataStore.data.first()
                preferences.asMap().forEach { (key, value) ->
                    // We only store strings and string-representations of other types,
                    // so we can safely cast the value.
                    if (value is String) {
                        jsonObject.put(key.name, value)
                    }
                }
            }
        }
        return jsonObject
    }

    private fun getBoolean(context: Context, key: String): Boolean {
        return get(context, key, "false")?.toBoolean() ?: false
    }

    private suspend fun putBoolean(context: Context, key: String, value: Boolean) {
        put(context, key, value.toString())
    }

    fun getTimeAdjustmentNotification(context: Context): Boolean {
        return getBoolean(context, "timeAdjustmentNotification")
    }

    suspend fun setTimeAdjustmentNotification(context: Context, value: Boolean) {
        putBoolean(context, "timeAdjustmentNotification", value)
    }

    fun getFineTimeAdjustment(context: Context): Int {
        return get(context, "fineTimeAdjustment", "0")?.toInt() ?: 0
    }

    suspend fun setFineTimeAdjustment(context: Context, fineTimeAdjustment: Int) {
        put(context, "fineTimeAdjustment", fineTimeAdjustment.toString())
    }

    fun getDeviceAddresses(context: Context): List<String> {
        val addresses = get(context, "DeviceAddresses", "") ?: ""
        return if (addresses.isEmpty()) emptyList() else addresses.split(",").distinct()
    }

    suspend fun addDeviceAddress(context: Context, address: String) {
        val addresses = getDeviceAddresses(context).toMutableList()
        if (!addresses.contains(address)) {
            addresses.add(address)
            put(context, "DeviceAddresses", addresses.joinToString(","))
        }
    }

    suspend fun removeDeviceAddress(context: Context, address: String) {
        val addresses = getDeviceAddresses(context).toMutableList()
        if (addresses.remove(address)) {
            put(context, "DeviceAddresses", addresses.joinToString(","))
            deleteAsync(context, "DeviceName_$address")
        }
    }

    fun getDeviceName(context: Context, address: String): String? {
        return get(context, "DeviceName_$address")
    }

    suspend fun setDeviceName(context: Context, address: String, name: String) {
        put(context, "DeviceName_$address", name)
    }
}
