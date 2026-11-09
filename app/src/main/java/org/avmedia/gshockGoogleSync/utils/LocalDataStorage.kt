package org.avmedia.gshockGoogleSync.utils

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object LocalDataStorage {

    private const val STORAGE_NAME = "CASIO_GOOGLE_SYNC_STORAGE"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Use a DataStore delegate in context
    private val Context.dataStore by preferencesDataStore(
        name = STORAGE_NAME,
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, STORAGE_NAME))
        }
    )

    // In-memory copy of the stored preferences, so synchronous reads never block on I/O or locks.
    @Volatile
    private var cache: Preferences? = null
    private val observing = AtomicBoolean(false)

    /** Call from Application.onCreate so the first synchronous read finds the cache ready. */
    fun preload(context: Context) {
        startObserving(context.applicationContext)
    }

    private fun startObserving(appContext: Context) {
        if (observing.compareAndSet(false, true)) {
            scope.launch {
                appContext.dataStore.data.collect { cache = it }
            }
        }
    }

    private fun snapshot(context: Context): Preferences {
        cache?.let { return it }
        val appContext = context.applicationContext
        startObserving(appContext)
        // Only reached if nothing has loaded the cache yet: one-time read.
        val loaded = runBlocking(Dispatchers.IO) { appContext.dataStore.data.first() }
        if (cache == null) cache = loaded
        return cache ?: loaded
    }

    fun put(context: Context, key: String, value: String) {
        scope.launch {
            putAndWait(context, key, value)
        }
    }

    suspend fun putAndWait(context: Context, key: String, value: String) {
        cache = context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }

    fun get(context: Context, key: String, defaultValue: String? = null): String? =
        snapshot(context)[stringPreferencesKey(key)] ?: defaultValue

    fun delete(context: Context, key: String) {
        scope.launch {
            deleteAsync(context, key)
        }
    }

    suspend fun deleteAsync(context: Context, key: String) {
        cache = context.dataStore.edit { preferences ->
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
        snapshot(context).asMap().forEach { (key, value) ->
            // We only store strings and string-representations of other types,
            // so we can safely cast the value.
            if (value is String) {
                jsonObject.put(key.name, value)
            }
        }
        return jsonObject
    }

    private fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return get(context, key, defaultValue.toString())?.toBoolean() ?: defaultValue
    }

    private suspend fun putBoolean(context: Context, key: String, value: Boolean) {
        putAndWait(context, key, value.toString())
    }

    fun getTimeAdjustmentNotification(context: Context): Boolean {
        return getBoolean(context, "timeAdjustmentNotification")
    }

    suspend fun setTimeAdjustmentNotification(context: Context, value: Boolean) {
        putBoolean(context, "timeAdjustmentNotification", value)
    }

    fun getVoiceVerbose(context: Context): Boolean {
        return getBoolean(context, "voiceVerbose", true)
    }

    suspend fun setVoiceVerbose(context: Context, value: Boolean) {
        putBoolean(context, "voiceVerbose", value)
    }

    fun getFineTimeAdjustment(context: Context): Int {
        return get(context, "fineTimeAdjustment", "0")?.toIntOrNull() ?: 0
    }

    suspend fun setFineTimeAdjustment(context: Context, fineTimeAdjustment: Int) {
        putAndWait(context, "fineTimeAdjustment", fineTimeAdjustment.toString())
    }

    fun getDeviceAddresses(context: Context): List<String> {
        val addresses = get(context, "DeviceAddresses", "") ?: ""
        return if (addresses.isEmpty()) emptyList() else addresses.split(",").distinct()
    }

    // The read-modify-write happens inside a single edit {}, which DataStore runs atomically.
    suspend fun addDeviceAddress(context: Context, address: String) {
        val key = stringPreferencesKey("DeviceAddresses")
        cache = context.dataStore.edit { preferences ->
            val addresses = preferences[key]
                ?.takeIf { it.isNotEmpty() }
                ?.split(",")
                ?.toMutableList() ?: mutableListOf()
            if (address !in addresses) {
                addresses.add(address)
                preferences[key] = addresses.joinToString(",")
            }
        }
    }

    suspend fun removeDeviceAddress(context: Context, address: String) {
        val addressesKey = stringPreferencesKey("DeviceAddresses")
        cache = context.dataStore.edit { preferences ->
            val addresses = preferences[addressesKey]
                ?.takeIf { it.isNotEmpty() }
                ?.split(",")
                ?.toMutableList() ?: mutableListOf()
            if (addresses.remove(address)) {
                preferences[addressesKey] = addresses.joinToString(",")
                preferences.remove(stringPreferencesKey("DeviceName_$address"))
            }
        }
    }

    fun getDeviceName(context: Context, address: String): String? {
        return get(context, "DeviceName_$address")
    }

    suspend fun setDeviceName(context: Context, address: String, name: String) {
        putAndWait(context, "DeviceName_$address", name)
    }
}
