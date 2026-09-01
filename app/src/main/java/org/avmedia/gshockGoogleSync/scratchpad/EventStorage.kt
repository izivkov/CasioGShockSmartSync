package org.avmedia.gshockGoogleSync.scratchpad

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages storage of event-related settings.
 * Packs manual mode (1 bit) into the scratchpad.
 */
@Singleton
class EventStorage @Inject constructor(
    private val manager: ScratchpadManager
) : ScratchpadClient {

    private var manualMode: Boolean = false

    init {
        manager.register(this)
    }

    override fun getBitSize(): Int = 1

    override fun decode(data: ByteArray) {
        if (data.isNotEmpty()) {
            manualMode = (data[0].toInt() and 1) == 1
        }
    }

    override fun encode(): ByteArray {
        val result = ByteArray(1)
        if (manualMode) {
            result[0] = 1.toByte()
        }
        return result
    }

    fun isManualMode(): Boolean = manualMode

    fun setManualMode(enabled: Boolean) {
        manualMode = enabled
    }

    suspend fun save() = manager.save()
    suspend fun load() = manager.load()
}
