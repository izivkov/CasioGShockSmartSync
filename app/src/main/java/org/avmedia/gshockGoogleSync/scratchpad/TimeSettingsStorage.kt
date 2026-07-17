package org.avmedia.gshockGoogleSync.scratchpad

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Manages storage of time-related settings.
 * Packs TimeZoneOption (4 values) into 2 bits.
 */
@Singleton
class TimeSettingsStorage @Inject constructor(
    private val manager: ScratchpadManager
) : ScratchpadClient {

    enum class TimeZoneOption {
        SYSTEM,
        LOCAL_MEAN_TIME,
        LOCAL_SOLAR_TIME,
        SIDEREAL_TIME
    }

    private var selectedOptionOrdinal: Int = TimeZoneOption.SYSTEM.ordinal

    companion object {
        private const val BITS_NEEDED = 2
    }

    init {
        manager.register(this)
    }

    override fun getBitSize(): Int = BITS_NEEDED

    override fun decode(data: ByteArray) {
        var ordinal = 0
        for (bit in 0 until BITS_NEEDED) {
            val byteIndex = bit / 8
            val bitPos = bit % 8
            if (byteIndex < data.size) {
                if ((data[byteIndex].toInt() shr bitPos) and 1 == 1) {
                    ordinal = ordinal or (1 shl bit)
                }
            }
        }
        // Validation
        if (ordinal !in TimeZoneOption.entries.indices) {
            ordinal = TimeZoneOption.SYSTEM.ordinal
        }
        selectedOptionOrdinal = ordinal
    }

    override fun encode(): ByteArray {
        val resultSize = ceil(getBitSize().toDouble() / 8.0).toInt()
        val result = ByteArray(resultSize)
        
        for (bit in 0 until BITS_NEEDED) {
            val byteIndex = bit / 8
            val bitPos = bit % 8
            if (byteIndex < result.size) {
                var currentByte = result[byteIndex].toInt()
                if ((selectedOptionOrdinal shr bit) and 1 == 1) {
                    currentByte = currentByte or (1 shl bitPos)
                } else {
                    currentByte = currentByte and (1 shl bitPos).inv()
                }
                result[byteIndex] = currentByte.toByte()
            }
        }
        return result
    }

    fun setTimeZoneOption(option: TimeZoneOption) {
        selectedOptionOrdinal = option.ordinal
    }

    fun getTimeZoneOption(): TimeZoneOption {
        return TimeZoneOption.entries.getOrElse(selectedOptionOrdinal) { TimeZoneOption.SYSTEM }
    }

    suspend fun save() = manager.save()
    suspend fun load() = manager.load()
}
