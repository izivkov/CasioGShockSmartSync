package org.avmedia.gshockGoogleSync.utils

import android.content.Context

/**
 * A dedicated class to manage the storage of alarm names.
 * This encapsulates the logic of how and where alarm names are stored,
 * decoupling the ViewModel from the underlying LocalDataStorage implementation.
 */
object AlarmNameStorage {

    private fun getAlarmKey(index: Int): String {
        return "alarm${index + 1}"
    }

    /**
     * Retrieves the name for a specific alarm.
     *
     * @param context The application context.
     * @param index The zero-based index of the alarm.
     * @return The alarm's name, or an empty string if not found.
     */
    fun get(context: Context, index: Int): String {
        return LocalDataStorage.get(context, getAlarmKey(index), "") ?: ""
    }

    /**
     * Saves the name for a specific alarm.
     *
     * @param context The application context.
     * @param index The zero-based index of the alarm.
     * @param name The name to save.
     */
    fun put(context: Context, index: Int, name: String) {
        LocalDataStorage.put(context, getAlarmKey(index), name)
    }

    /**
     * Clears the name for a specific alarm.
     *
     * @param context The application context.
     * @param index The zero-based index of the alarm.
     */
    fun clear(context: Context, index: Int) {
        LocalDataStorage.put(context, getAlarmKey(index), "")
    }
}