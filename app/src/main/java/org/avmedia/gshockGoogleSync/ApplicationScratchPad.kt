package org.avmedia.gshockGoogleSync

import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.GShockAPI

object ApplicationScratchPad {

    internal lateinit var gshockAPI: GShockAPI

    fun setGShockAPI(gshockAPI: GShockAPI) {
        this.gshockAPI = gshockAPI
    }

    private val buffer: ByteArray
        get() = runBlocking { // Use runBlocking for simplicity inside a property getter
            gshockAPI.getAppInfoUserBuffer()
        }

    // This function now writes the buffer via the injected repository.
    private fun writeBuffer(newBuffer: ByteArray) {
        runBlocking { // Use runBlocking to call the suspend function
            gshockAPI.setAppInfoUserBuffer(newBuffer)
        }
    }

    // Constants can be moved out of the companion object
    private val NUM_ALARMS = 5
    private val BITS_PER_ALARM = 3 // 0..5 fits in 3 bits
    private val ALARMS_BYTE_OFFSET = 0 // start at first byte of user buffer

    /** Get all 5 alarm codes from buffer */
    fun getAlarmCodes(): IntArray {
        val buf = buffer
        val result = IntArray(NUM_ALARMS)

        // Pack: 5×3=15 bits → 2 bytes
        val combined =
            ((buf[ALARMS_BYTE_OFFSET].toInt() and 0xFF) shl 8) or (buf[ALARMS_BYTE_OFFSET + 1].toInt() and 0xFF)

        for (i in 0 until NUM_ALARMS) {
            val shift = (NUM_ALARMS - 1 - i) * BITS_PER_ALARM
            result[i] = (combined shr shift) and 0x07 // mask 3 bits
        }
        return result
    }

    /** Set all 5 alarm codes */
    fun setAlarmCodes(codes: IntArray) {
        require(codes.size == NUM_ALARMS) { "Must provide exactly $NUM_ALARMS alarm codes" }
        codes.forEach { require(it in 0..5) { "Alarm code must be 0..5, got $it" } }

        // Pack codes into 15 bits
        var combined = 0
        for (i in 0 until NUM_ALARMS) {
            val shift = (NUM_ALARMS - 1 - i) * BITS_PER_ALARM
            combined = combined or ((codes[i] and 0x07) shl shift)
        }

        // Split into 2 bytes
        val newBuf = buffer.copyOf()
        newBuf[ALARMS_BYTE_OFFSET] = ((combined shr 8) and 0xFF).toByte()
        newBuf[ALARMS_BYTE_OFFSET + 1] = (combined and 0xFF).toByte()

        // Write back to AppInfoIO buffer
        writeBuffer(newBuf)
    }

    /** Get a single alarm code */
    fun getAlarm(index: Int): Int {
        require(index in 0 until NUM_ALARMS)
        return getAlarmCodes()[index]
    }

    /** Set a single alarm code */
    fun setAlarm(index: Int, value: Int) {
        require(index in 0 until NUM_ALARMS)
        require(value in 0..5)
        val codes = getAlarmCodes()
        codes[index] = value
        setAlarmCodes(codes)
    }
}
