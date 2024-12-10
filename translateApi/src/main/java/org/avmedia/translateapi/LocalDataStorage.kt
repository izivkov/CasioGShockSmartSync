package org.avmedia.translateapi

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

    private const val STORAGE_NAME = "Dynamic Resource Translator"
    private val scope = CoroutineScope(Dispatchers.IO)

    // Use a DataStore delegate in context
    private val Context.dataStore by preferencesDataStore(
        name = STORAGE_NAME,
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, STORAGE_NAME))
        }
    )

    private fun put(context: Context, key: Int, value: String) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[stringPreferencesKey(key.toString())] = value
            }
        }
    }

    fun putResource(context: Context, key: ResourceLocaleKey, value: String) {
        put(context, key.hashCode(), value)
    }


    private fun get(context: Context, key: Int, defaultValue: String? = null): String? {
        var value: String?
        runBlocking {
            val preferences = context.dataStore.data.first()
            value = preferences[stringPreferencesKey(key.toString())] ?: defaultValue
        }
        return value
    }

    fun getResource(context: Context, key: ResourceLocaleKey, defaultValue: String? = null): String? {
        return get (context, key.hashCode(), defaultValue)
    }

    fun delete(context: Context, key: String) {
        scope.launch {
            deleteAsync(context, key)
        }
    }

    private suspend fun deleteAsync(context: Context, key: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
        }
    }
}
