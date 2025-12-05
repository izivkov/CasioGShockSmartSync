package org.avmedia.gshockGoogleSync.scratchpad

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * A utility class providing a static lookup set for standard alarm names.
 * These names correspond to specific indices used in the bit-packed storage format.
 */
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
 * Manages the persistent storage of custom user-defined names for alarms.
 *
 * This class implements [ScratchpadClient] to handle a specific slice of the scratchpad memory.
 * It uses a bit-packing strategy to store name references (indices) efficiently within the limited byte array.
 *
 * Key features:
 * - Maps string names to integer codes (and vice-versa).
 * - Packs/unpacks these 3-bit codes into the byte buffer.
 * - Provides high-level methods ([put], [get]) for ViewModel interaction.
 */
@Singleton
class AlarmNameStorage @Inject constructor(
    private val manager: ScratchpadManager
) : ScratchpadClient {
    private var namesMap: Map<Int, String> = emptyMap()
    private var codesMap: Map<String, Int> = emptyMap()

    // This is the client's local copy of its data slice.
    // Initialize with size calculated by the override.
    private var scratchpadBuffer: ByteArray = ByteArray(getStorageSize())

    companion object {
        private const val ALARM_COUNT = 6
        // The number of bits required to store one alarm's name code.
        // Since we have 6 names (0-5) + a "no name" state (7), we need 3 bits (2^3 = 8 combinations).
        private const val BITS_PER_ALARM = 3
        private const val NO_NAME_INDEX = 0x7
    }

    init {
        // Registration must happen before setNames so the buffer is correctly sized.
        manager.register(this)
        setNames(AlarmsLookupSet.alarmCodeSet)
    }

    // --- Implementation of ScratchpadClient Interface ---

    override fun getStorageOffset(): Int {
        // AlarmNameStorage starts at position 0 in the scratchpad buffer
        return 0
    }

    override fun getStorageSize(): Int {
        // Calculate the storage size dynamically.
        // Total bits needed = ALARM_COUNT * BITS_PER_ALARM
        // Bytes needed = ceil(Total bits / 8)
        val totalBits = ALARM_COUNT * BITS_PER_ALARM
        return ceil(totalBits.toDouble() / 8.0).toInt()
    }

    override fun setBuffer(buffer: ByteArray) {
        // The manager gives us our slice. We must ensure we don't take a buffer of the wrong size.
        if (buffer.size == getStorageSize()) {
            this.scratchpadBuffer = buffer
        }
    }

    override fun getBuffer(): ByteArray {
        return this.scratchpadBuffer
    }

    // --- Public API for ViewModel/Use-Cases ---

    /**
     * Sets the master list of possible names that can be used for alarms.
     */
    fun setNames(names: Set<Pair<Int, String>>) {
        namesMap = names.associate { it.first to it.second }
        codesMap = names.associate { it.second to it.first }
    }

    /**
     * Updates an alarm's name in the local in-memory buffer using bit packing.
     * This is a fast, in-memory operation. The manager handles persisting it.
     */
    fun put(name: String, index: Int)  {
        if (index !in 0 until ALARM_COUNT) return
        val code = codesMap[name] ?: NO_NAME_INDEX
        updateLocalBuffer(code, index)
    }

    /**
     * Retrieves a name from the in-memory buffer, unpacking it from its bit-packed code.
     */
    fun get(index: Int): String {
        if (index !in 0 until ALARM_COUNT) return ""

        // Reverting to the simpler, effective logic for 3-bit packing:
        val localByteIndex = index / 2
        val isLowerBits = (index % 2) == 0

        if (localByteIndex >= scratchpadBuffer.size) return "" // Safety check

        val byteValue = scratchpadBuffer[localByteIndex].toInt() and 0xFF
        val code = if (isLowerBits) {
            byteValue and 0x07 // Read the lower 3 bits
        } else {
            (byteValue shr 4) and 0x07 // Read the shifted upper 3 bits
        }

        return if (code == NO_NAME_INDEX) "" else namesMap[code] ?: ""
    }

    /**
     * Resets the client's internal buffer to a cleared state.
     */
    fun clear() {
        for (i in 0 until ALARM_COUNT) {
            val byteIndex = i / 2
            val isLowerBits = (i % 2) == 0
            
            if (byteIndex >= scratchpadBuffer.size) continue

            val currentByte = scratchpadBuffer[byteIndex].toInt() and 0xFF
            scratchpadBuffer[byteIndex] = if (isLowerBits) {
                ((currentByte and 0xF0) or NO_NAME_INDEX).toByte()
            } else {
                ((currentByte and 0x0F) or (NO_NAME_INDEX shl 4)).toByte()
            }
        }
    }

    suspend fun save () {
        manager.save()
    }

    /**
     * Loads the data from the watch into the local buffer.
     */
    suspend fun load() {
        manager.load()
    }

    /*
    * Private helpers
     */
    private fun updateLocalBuffer(code: Int, index: Int) {
        val byteIndex = index / 2
        val isLowerBits = (index % 2) == 0

        if (byteIndex >= scratchpadBuffer.size) return // Safety check

        val currentByte = scratchpadBuffer[byteIndex].toInt() and 0xFF
        scratchpadBuffer[byteIndex] = if (isLowerBits) {
            ((currentByte and 0xF0) or code).toByte()
        } else {
            ((currentByte and 0x0F) or (code shl 4)).toByte()
        }
    }
}
