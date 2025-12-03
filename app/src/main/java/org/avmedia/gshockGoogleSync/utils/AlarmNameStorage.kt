package org.avmedia.gshockGoogleSync.utils

import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import javax.inject.Inject
import javax.inject.Singleton

class AlarmsLookupSet {
    enum class AlarmCode(val value: Int) {
        Daily(0),
        Fajr(1),
        Dhuhr(2),
        Asr(3),
        Maghrib(4),
        Isha(5)
    }

    companion object {
        val alarmCodeSet = setOf(
            AlarmCode.Daily.value to "Daily",
            AlarmCode.Fajr.value to "Fajr",
            AlarmCode.Dhuhr.value to "Dhuhr",
            AlarmCode.Asr.value to "Asr",
            AlarmCode.Maghrib.value to "Maghrib",
            AlarmCode.Isha.value to "Isha"
        )
    }
}

/**
 * Manages storage of custom alarm names directly on the watch's scratchpad.
 * This decouples the ViewModel from the underlying storage implementation.
 *
 * This class is stateful. The list of supported names must be set via `setNames()`
 * before calling `put()` or `get()`.
 */
@Singleton
class AlarmNameStorage @Inject constructor(
    private val api: GShockRepository,
) {
    val alarmsSet: AlarmsLookupSet = AlarmsLookupSet()
    private var namesMap: Map<Int, String> = emptyMap()
    private var codesMap: Map<String, Int> = emptyMap()

    // In-memory buffer for scratchpad data. Initialize with "no name" value.
    private var scratchpadBuffer: ByteArray = ByteArray(SCRATCHPAD_STORAGE_SIZE) { 0 }

    companion object {
        private const val ALARM_COUNT = 6
        private const val SCRATCHPAD_STORAGE_INDEX = 0
        // We can store 2 alarm codes (3 bits each) per byte. For 6 alarms, we need 3 bytes.
        private const val SCRATCHPAD_STORAGE_SIZE = 3
        // Use 7 (binary 111) as the "no name" index, as it's the highest 3-bit value.
        private const val NO_NAME_INDEX = 0x7
    }

    init {
        setNames(AlarmsLookupSet.alarmCodeSet)
    }
    /**
     * Sets the master list of possible names that can be used for alarms.
     * This should be called before using `get` or `put`.
     * @param names A set of pairs mapping integer codes to string names.
     */
    fun setNames(names: Set<Pair<Int, String>>) {
        namesMap = names.associate { it.first to it.second }
        codesMap = names.associate { it.second to it.first }
        // Also load the current state from the watch when names are set.
        // kotlinx.coroutines.runBlocking { load() }
    }

    /**
     * Loads the current scratchpad data from the watch into the local buffer.
     * Should be called before a batch of `put` operations.
     */
    suspend fun load() {
        scratchpadBuffer = api.getScratchpadData(SCRATCHPAD_STORAGE_INDEX, SCRATCHPAD_STORAGE_SIZE)
    }

    /**
     * Saves the current in-memory buffer to the watch's scratchpad.
     * Should be called after a batch of `put` operations.
     */
    suspend fun save() {
        api.setScratchpadData(scratchpadBuffer, SCRATCHPAD_STORAGE_INDEX)
    }

    /**
     * Updates an alarm's name in the local in-memory buffer using 3-bit packing.
     * This does NOT write to the watch. Call `save()` to persist changes.
     *
     * @param name The custom name for the alarm (e.g., "Fajr").
     * @param index The zero-based index of the alarm on the watch (0-5).
     */
    fun put(name: String, index: Int)  {
        // 1. Validate the alarm index.
        if (index !in 0 until ALARM_COUNT) return

        // 2. Find the integer code for the name, defaulting to NO_NAME_INDEX if not found.
        val code = codesMap[name] ?: NO_NAME_INDEX

        // 3. Determine which byte and which part of the byte to update.
        val byteIndex = index / 2
        val isLowerBits = (index % 2) == 0

        // 4. Update the in-memory buffer.
        val currentByte = scratchpadBuffer[byteIndex].toInt() and 0xFF
        scratchpadBuffer[byteIndex] = if (isLowerBits) {
            // Clear the lower 4 bits and set the new code. Keep the upper 4 bits.
            ((currentByte and 0xF0) or code).toByte()
        } else {
            // Clear the upper 4 bits and set the new code shifted. Keep the lower 4 bits.
            ((currentByte and 0x0F) or (code shl 4)).toByte()
        }
    }

    /**
     * Retrieves a name from the in-memory buffer, unpacking it from its 3-bit code.
     */
    fun get(index: Int): String {
        // 1. Validate the alarm index.
        if (index !in 0..5) return ""

        // 2. Determine which byte to read from.
        val byteIndex = index / 2
        val isLowerBits = (index % 2) == 0

        // 3. Extract the 3-bit code from the byte.
        val byteValue = scratchpadBuffer[byteIndex].toInt() and 0xFF
        val code = if (isLowerBits) {
            byteValue and 0x07 // Read the lower 3 bits
        } else {
            (byteValue shr 4) and 0x07 // Read the shifted upper 3 bits
        }

        // 4. Look up the name from the code.
        return if (code == NO_NAME_INDEX) "" else namesMap[code] ?: ""
    }

    /**
     * Clears all custom alarm names from the scratchpad.
     */
    suspend fun clear() {
        // Create a new buffer where every 3-bit slot is set to NO_NAME_INDEX.
        val clearedBuffer = ByteArray(SCRATCHPAD_STORAGE_SIZE)
        for (i in 0 until ALARM_COUNT) {
            updateBuffer(NO_NAME_INDEX, i) // Use the existing helper
        }

        // Write this cleared buffer to the watch and update the local state.
        api.setScratchpadData(clearedBuffer, SCRATCHPAD_STORAGE_INDEX)
        scratchpadBuffer = clearedBuffer.clone()
    }

    private fun updateBuffer(code: Int, index: Int) {
        val byteIndex = index / 2
        val isLowerBits = (index % 2) == 0

        val currentByte = scratchpadBuffer[byteIndex].toInt() and 0xFF
        scratchpadBuffer[byteIndex] = if (isLowerBits) {
            // Clear the lower 4 bits and set the new code. Keep the upper 4 bits.
            ((currentByte and 0xF0) or code).toByte()
        } else {
            // Clear the upper 4 bits and set the new code shifted. Keep the lower 4 bits.
            ((currentByte and 0x0F) or (code shl 4)).toByte()
        }
    }

    /**
     * Returns the storage configuration.
     * @return A Pair where the first element is the size in bytes (Int) and
     *         the second element is the starting index (Int).
     */
    fun getStorageInfo(): Pair<Int, Int> {
        return Pair(SCRATCHPAD_STORAGE_SIZE, SCRATCHPAD_STORAGE_INDEX)
    }
}
