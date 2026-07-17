package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.Utils.hexToBytes
import timber.log.Timber

// ============================================================================
// Pure Functional Core: App Info Scratchpad Processing
// ============================================================================

/**
 * Pure functional core for app info scratchpad operations.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AppInfoIOFunctional {
    const val BUFFER_SIZE = 12
    const val CMD_BYTE_INDEX = 0
    const val VERSION_INDEX = 1
    const val USER_DATA_START_INDEX = 2
    const val LEGACY_VERSION = 0x93
    const val CURRENT_VERSION = 1

    fun isScratchpadInitialized(buffer: ByteArray): Boolean =
        buffer.getOrNull(VERSION_INDEX) != null && buffer[VERSION_INDEX] != 0xFF.toByte()

    fun needsReinit(buffer: ByteArray): Boolean =
        !isScratchpadInitialized(buffer)

    fun createInitializedBuffer(version: Int): ByteArray = ByteArray(BUFFER_SIZE).apply {
        this[CMD_BYTE_INDEX] = 0x22.toByte()
        this[VERSION_INDEX] = version.toByte()
    }

    fun readUserData(buffer: ByteArray, startIndex: Int, len: Int): Result<ByteArray> {
        val userDataSize = BUFFER_SIZE - USER_DATA_START_INDEX
        if (startIndex < 0 || len < 0 || startIndex + len > userDataSize) {
            return Result.failure(IllegalArgumentException("Invalid read bounds"))
        }
        return Result.success(buffer.copyOfRange(
            USER_DATA_START_INDEX + startIndex,
            USER_DATA_START_INDEX + startIndex + len
        ))
    }

    fun migrate(
        oldData: ByteArray,
        oldLayout: Map<String, IntArray>,
        newLayout: Map<String, IntArray>
    ): ByteArray {
        val newData = ByteArray(BUFFER_SIZE - USER_DATA_START_INDEX)
        
        newLayout.forEach { (key, newPiece) ->
            oldLayout[key]?.let { oldPiece ->
                val value = extractBits(oldData, oldPiece[0], oldPiece[1])
                insertBits(newData, newPiece[0], newPiece[1], value)
            }
        }
        
        return newData
    }

    private fun extractBits(data: ByteArray, bitOffset: Int, bitLength: Int): Int {
        var value = 0
        for (i in 0 until bitLength) {
            val currentBit = bitOffset + i
            val byteIndex = currentBit / 8
            val bitPos = currentBit % 8
            if (byteIndex < data.size) {
                if ((data[byteIndex].toInt() shr bitPos) and 1 == 1) {
                    value = value or (1 shl i)
                }
            }
        }
        return value
    }

    private fun insertBits(data: ByteArray, bitOffset: Int, bitLength: Int, value: Int) {
        for (i in 0 until bitLength) {
            val currentBit = bitOffset + i
            val byteIndex = currentBit / 8
            val bitPos = currentBit % 8
            if (byteIndex < data.size) {
                var currentByte = data[byteIndex].toInt()
                if ((value shr i) and 1 == 1) {
                    currentByte = currentByte or (1 shl bitPos)
                } else {
                    currentByte = currentByte and (1 shl bitPos).inv()
                }
                data[byteIndex] = currentByte.toByte()
            }
        }
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

@RequiresApi(Build.VERSION_CODES.O)
object AppInfoIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()
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
        lastReceivedData = Utils.toCompactString(data).hexToBytes()

        if (AppInfoIOFunctional.needsReinit(lastReceivedData)) {
            initScratchPad(AppInfoIOFunctional.CURRENT_VERSION)
        }

        state.deferredResult?.complete(data)
        state = State()
    }

    private fun initScratchPad(version: Int) {
        val buffer = AppInfoIOFunctional.createInitializedBuffer(version)
        IO.writeCmd(GetSetMode.SET, buffer)
        lastReceivedData = buffer
        wasScratchpadReset = true
    }

    fun shouldMigrate(): Boolean = 
        lastReceivedData.size > AppInfoIOFunctional.VERSION_INDEX && 
        lastReceivedData[AppInfoIOFunctional.VERSION_INDEX] == AppInfoIOFunctional.LEGACY_VERSION.toByte()

    fun setUserData(data: ByteArray) {
        if (!AppInfoIOFunctional.isScratchpadInitialized(lastReceivedData)) {
            throw IllegalStateException("Scratchpad not initialized")
        }

        val newBuffer = AppInfoIOFunctional.createInitializedBuffer(AppInfoIOFunctional.CURRENT_VERSION)
        val userDataSize = AppInfoIOFunctional.BUFFER_SIZE - AppInfoIOFunctional.USER_DATA_START_INDEX
        System.arraycopy(data, 0, newBuffer, AppInfoIOFunctional.USER_DATA_START_INDEX, Math.min(data.size, userDataSize))

        lastReceivedData = newBuffer
        IO.writeCmd(GetSetMode.SET, lastReceivedData)
    }

    fun getUserData(index: Int, len: Int): ByteArray {
        if (!AppInfoIOFunctional.isScratchpadInitialized(lastReceivedData)) {
            throw IllegalStateException("Scratchpad not initialized")
        }

        return AppInfoIOFunctional.readUserData(lastReceivedData, index, len)
            .getOrThrow()
    }
}
