package org.avmedia.gshockGoogleSync.scratchpad

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages storage of time-related settings in the scratchpad.
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

    private var scratchpadBuffer: ByteArray = ByteArray(getStorageSize())

    init {
        manager.register(this)
    }

    override fun getStorageOffset(): Int = 5

    override fun getStorageSize(): Int = 1

    override fun setBuffer(buffer: ByteArray) {
        if (buffer.size == getStorageSize()) {
            this.scratchpadBuffer = buffer
        }
    }

    override fun getBuffer(): ByteArray = this.scratchpadBuffer

    fun setTimeZoneOption(option: TimeZoneOption) {
        val value = option.ordinal
        // Store in the first 2 bits of the first byte
        scratchpadBuffer[0] = ((scratchpadBuffer[0].toInt() and 0xFC) or (value and 0x03)).toByte()
    }

    fun getTimeZoneOption(): TimeZoneOption {
        val value = scratchpadBuffer[0].toInt() and 0x03
        return TimeZoneOption.entries.getOrElse(value) { TimeZoneOption.SYSTEM }
    }

    suspend fun save() {
        manager.save()
    }

    suspend fun load() {
        manager.load()
    }
}
