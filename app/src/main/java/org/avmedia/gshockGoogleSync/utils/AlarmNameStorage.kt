package org.avmedia.gshockGoogleSync.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockapi.GShockAPI

/**
 * Manages storage of custom alarm names directly on the watch's scratchpad.
 * This decouples the ViewModel from the underlying storage implementation.
 *
 * Alarms at indices 0-5 can be assigned custom names from a predefined list
 * provided by the caller.
 */
object AlarmNameStorage {
    private const val SCRATCHPAD_STORAGE_INDEX = 0

    // We need to store mappings for 6 alarms (0-5).
    // Let's use one byte per alarm index for simplicity and future expansion.
    // The byte will contain the index from the provided `supportedNames` list or a special value for 'no name'.
    private const val SCRATCHPAD_STORAGE_SIZE = 6
    private const val NO_NAME_INDEX = 0xFF // Special value for no assigned custom name

    private var namesMap: Map<Int, String> = emptyMap()

    // val api = GShockAPI(ApplicationContext() as Context)

    /**
     * Decodes a byte array from the scratchpad into a map of [Watch Alarm Index -> Custom Name].
     * Each byte in the array corresponds to a watch alarm (index 0 to 5).
     */
    private fun decodeBytesToNamesMap(
        bytes: ByteArray,
        supportedNames: List<String>
    ): Map<Int, String> {
        val resultMap = mutableMapOf<Int, String>()
        bytes.forEachIndexed { watchAlarmIndex, nameIndexByte ->
            val nameIndex = nameIndexByte.toInt() and 0xFF
            if (nameIndex != NO_NAME_INDEX) {
                supportedNames.getOrNull(nameIndex)?.let { name ->
                    resultMap[watchAlarmIndex] = name
                }
            }
        }
        return resultMap
    }

    /**
     * Encodes a map of [Watch Alarm Index -> Custom Name] into a compact byte array
     * based on a provided list of supported names.
     */
    private fun encodeNamesMapToBytes(
        map: Map<Int, String>,
        supportedNames: List<String>
    ): ByteArray {
        val bytes = ByteArray(SCRATCHPAD_STORAGE_SIZE) { NO_NAME_INDEX.toByte() }
        map.forEach { (watchAlarmIndex, name) ->
            if (watchAlarmIndex in 0 until SCRATCHPAD_STORAGE_SIZE) {
                val nameIndex = supportedNames.indexOf(name)
                if (nameIndex != -1) {
                    bytes[watchAlarmIndex] = nameIndex.toByte()
                }
            }
        }
        return bytes
    }

    /**
     * Retrieves the name for a specific alarm.
     *
     * @param index The zero-based index of the alarm on the watch.
     * @param supportedNames The list of all possible names to decode against.
     * @return The alarm's custom name, or an empty string if not found.
     */
    suspend fun get(index: Int, supportedNames: List<String>): String {
        // Read fresh data from the watch to build the map
        val bytes = api.getScratchpadData(SCRATCHPAD_STORAGE_INDEX, SCRATCHPAD_STORAGE_SIZE)
        namesMap = decodeBytesToNamesMap(bytes, supportedNames)

        return namesMap[index] ?: ""
    }

    suspend fun get(context: Context, index: Int): String {
        return ""
    }

    /**
     * Saves a map of alarm configurations to the scratchpad.
     *
     * @param map A map where the key is the watch alarm index (0-5) and the value is the custom name.
     * @param supportedNames The master list of all possible names that can be assigned.
     */
    suspend fun put(map: Map<Int, String>, supportedNames: List<String>) {
        val bytesToStore = encodeNamesMapToBytes(map, supportedNames)
        api.setScratchpadData(bytesToStore, SCRATCHPAD_STORAGE_INDEX)
        namesMap = map // Update local cache
    }

    /**
     * Clears all custom alarm names from the scratchpad.
     */
    suspend fun clear() {
        val clearedBytes = ByteArray(SCRATCHPAD_STORAGE_SIZE) { NO_NAME_INDEX.toByte() }
        api.setScratchpadData(clearedBytes, SCRATCHPAD_STORAGE_INDEX)
        namesMap = emptyMap() // Clear local cache
    }
}