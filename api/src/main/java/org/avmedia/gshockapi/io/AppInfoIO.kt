package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.Utils.hexToBytes
import java.security.SecureRandom
import kotlin.text.toHexString

object AppInfoIO {
    // Constants for buffer structure and validation
    private const val BUFFER_SIZE = 12
    private const val CMD_BYTE_INDEX = 0
    private const val MAGIC_NUMBER_INDEX = 1
    private const val USER_DATA_START_INDEX = 2 // User data starts after the magic number

    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()
    private var magicNumber = 0x93
    var lastReceivedData: ByteArray = ByteArray(0)
        private set

    var wasScratchpadReset = false


    suspend fun request(): String {
        return CachedIO.request("22") { key ->
            state = state.copy(deferredResult = CompletableDeferred())
            IO.request(key)
            state.deferredResult?.await() ?: ""
        }
    }

    fun onReceived(data: String) {
        wasScratchpadReset = false
        // App info:
        // This is needed to re-enable button D (Lower-right) after the watch has been reset
        // or BLE has been cleared.
        // It is a hard-coded value, which is what the official app does as well.

        // If watch was reset, the app info will come as:
        // 0x22 FF FF FF FF FF FF FF FF FF FF 00
        // In this case, set it to the hardcoded value bellow, so 'D' button will work again.
        lastReceivedData = Utils.toCompactString(data).hexToBytes()
        initScratchPad()

        state.deferredResult?.complete(data)
        state = State()
    }

    private fun initScratchPad() {
        // Initialize the magic number
        // Check if the magic number is already set. If so, do nothing.
        // The first byte (index 0) is the command '0x22', the data starts at index 1.
        if (lastReceivedData.size > 1 && lastReceivedData[1] == magicNumber.toByte()) {
            return
        }

        // Initialize the buffer. The total length of the characteristic data is 12 bytes.
        // The first byte is the command '0x22'. The next 11 bytes are the payload.
        val buffer = ByteArray(BUFFER_SIZE)
        buffer[CMD_BYTE_INDEX] = 0x22.toByte() // Command
        buffer[MAGIC_NUMBER_INDEX] = magicNumber.toByte() // Our magic number

        // Initialize the rest of the payload buffer with 0's as a neutral default
        for (i in USER_DATA_START_INDEX until buffer.size) {
            buffer[i] = 0.toByte()
        }

        // Send the initialized buffer to the watch and update our local copy
        IO.writeCmd(GetSetMode.SET, buffer.toHexString())
        lastReceivedData = buffer
        wasScratchpadReset = true
    }

    /**
     * Stores an array of bytes in the user data area of the scratchpad.
     * This function updates the local state and sends the change to the watch.
     *
     * @param data The byte array to write.
     * @param startIndex The zero-based index within the user data area where writing should begin.
     * @throws IllegalStateException if the scratchpad is not initialized or data exceeds buffer size.
     */
    fun setUserData(data: ByteArray, startIndex: Int) {
        // 1. Check if the scratchpad is initialized with our magic number.
        if (lastReceivedData.getOrNull(MAGIC_NUMBER_INDEX) != magicNumber.toByte()) {
            throw IllegalStateException("Scratchpad not initialized. Magic number missing.")
        }

        val userDataSize = BUFFER_SIZE - USER_DATA_START_INDEX
        val writeStartIndexInMainBuffer = USER_DATA_START_INDEX + startIndex

        // 2. Check if the new data will fit in the user data area.
        if (startIndex < 0 || startIndex + data.size > userDataSize) {
            throw IllegalStateException("Data exceeds user data buffer size. Max size: $userDataSize bytes.")
        }

        // 3. Copy the new data into our local buffer at the correct position.
        System.arraycopy(data, 0, lastReceivedData, writeStartIndexInMainBuffer, data.size)

        // 4. Send the modified local buffer back to the watch.
        IO.writeCmd(GetSetMode.SET, lastReceivedData.toHexString())
    }

    /**
     * Retrieves a segment of byte data from the user data area of the scratchpad.
     *
     * @param index The zero-based starting index within the user data area.
     * @param len The number of bytes to retrieve.
     * @return A [ByteArray] containing the requested data.
     * @throws IllegalStateException if the scratchpad is not initialized.
     * @throws IllegalArgumentException if the requested index or length is out of bounds.
     */
    fun getUserData(index: Int, len: Int): ByteArray {
        // 1. Check if the scratchpad is initialized with our magic number.
        if (lastReceivedData.getOrNull(MAGIC_NUMBER_INDEX) != magicNumber.toByte()) {
            throw IllegalStateException("Scratchpad not initialized. Magic number missing.")
        }

        val userDataSize = BUFFER_SIZE - USER_DATA_START_INDEX
        val readStartIndexInMainBuffer = USER_DATA_START_INDEX + index

        // 2. Validate the read request boundaries.
        if (index < 0 || len < 0 || index + len > userDataSize) {
            throw IllegalArgumentException("Invalid read request. Index or length is out of the user data bounds.")
        }

        // 3. Extract and return the requested portion of the array.
        return lastReceivedData.copyOfRange(readStartIndexInMainBuffer, readStartIndexInMainBuffer + len)
    }
}
