package org.avmedia.gshockGoogleSync.utils

import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages storage of custom alarm names directly on the watch's scratchpad.
 * This decouples the ViewModel from the underlying storage implementation.
 *
 * Alarms at indices 0-5 can be assigned custom names from a predefined list
 * provided by the caller via `setSupportedNames`.
 */

@Singleton
class AlarmNameStorage @Inject constructor(
    private val api: GShockRepository,
) {
    private var namesMap: Map<Int, String> = emptyMap()
    private var supportedNames: List<String> = emptyList()

    companion object {
        private const val SCRATCHPAD_STORAGE_INDEX = 0
        private const val SCRATCHPAD_STORAGE_SIZE = 6
        private const val NO_NAME_INDEX = 0xFF
    }

    /**
     * Sets the master list of possible names that can be used for alarms.
     * This should be called before using `get` or `put`.
     * @param names A list of all possible alarm names.
     */
    fun setSupportedNames(names: List<String>) {
        supportedNames = names
    }

    /**
     * Decodes a byte array from the scratchpad into a map of [Watch Alarm Index -> Custom Name].
     */
    private fun decodeBytesToNamesMap(
        bytes: ByteArray
    ): Map<Int, String> {
        if (supportedNames.isEmpty()) {
            // Cannot decode without the names list, return empty.
            return emptyMap()
        }
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
     * Encodes a map of [Watch Alarm Index -> Custom Name] into a compact byte array.
     */
    private fun encodeNamesMapToBytes(
        map: Map<Int, String>
    ): ByteArray {
        val bytes = ByteArray(SCRATCHPAD_STORAGE_SIZE) { NO_NAME_INDEX.toByte() }
        if (supportedNames.isNotEmpty()) {
            map.forEach { (watchAlarmIndex, name) ->
                if (watchAlarmIndex in 0 until SCRATCHPAD_STORAGE_SIZE) {
                    val nameIndex = supportedNames.indexOf(name)
                    if (nameIndex != -1) {
                        bytes[watchAlarmIndex] = nameIndex.toByte()
                    }
                }
            }
        }
        return bytes
    }

    /**
     * Retrieves the name for a specific alarm using the internally stored supported names.
     */
    suspend fun get(index: Int): String {
        val bytes = api.getScratchpadData(SCRATCHPAD_STORAGE_INDEX, SCRATCHPAD_STORAGE_SIZE)
        namesMap = decodeBytesToNamesMap(bytes)
        return namesMap[index] ?: ""
    }

    /**
     * Saves a map of alarm configurations to the scratchpad using the internally stored supported names.
     */
    suspend fun put(map: Map<Int, String>) {
        val bytesToStore = encodeNamesMapToBytes(map)
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
