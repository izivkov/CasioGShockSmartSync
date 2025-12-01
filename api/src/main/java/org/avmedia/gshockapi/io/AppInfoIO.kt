package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.Utils.hexToBytes
import java.security.SecureRandom

object AppInfoIO {

    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    // --- Magic number ---
    private var MAGIC_BYTES = byteArrayOf(0xB1.toByte(), 0xFA.toByte())

    // --- Buffer and user data region ---
    private var currentBuf: ByteArray = ByteArray(12) { 0 }
    private const val USER_DATA_OFFSET = 3

    // All remaining bytes after the magic marker
    private val userDataSize: Int
        get() = currentBuf.size - USER_DATA_OFFSET

    // --- Suspendable request ---
    suspend fun request(): String {
        return CachedIO.request("22") { key ->
            state = state.copy(deferredResult = CompletableDeferred())
            IO.request(key)
            state.deferredResult?.await() ?: ""
        }
    }

    // --- Handle incoming app info ---
    fun onReceived(data: String) {
        val appInfoCompactStr = Utils.toCompactString(data)
        validateLength(appInfoCompactStr)

        val incoming = appInfoCompactStr.hexToBytes()
        val isResetPattern = isResetPattern(appInfoCompactStr)
        val hasMagic = hasMagicBytes(incoming)

        // If not reset and already has magic → do nothing
        if (!isResetPattern && hasMagic) {
            complete(appInfoCompactStr)
            currentBuf = incoming.copyOf()
            return
        }

        // Build base buffer (reset uses hardcoded block)
        val baseBuf = buildBaseBuffer(incoming, isResetPattern)

        // Insert magic bytes
        insertMagic(baseBuf)

        // Update current buffer in memory
        currentBuf = baseBuf.copyOf()

        val finalHex = Utils.fromByteArrayToHexStr(baseBuf)
        IO.writeCmd(GetSetMode.SET, finalHex)

        complete(finalHex)
    }

    private fun validateLength(hex: String) {
        if (hex.length != 24) {
            throw IllegalArgumentException(
                "AppInfo must be 12 bytes (24 hex chars): got '$hex'"
            )
        }
    }

    private const val RESET_PATTERN = "22FFFFFFFFFFFFFFFFFFFF00"
    private fun isResetPattern(hex: String): Boolean =
        hex.equals(RESET_PATTERN, ignoreCase = true)

    private const val HARDCODED_BLOCK_HEX = "22B1FAEA51BD2F085F461502"
    private fun buildBaseBuffer(incoming: ByteArray, isReset: Boolean): ByteArray =
        if (isReset) HARDCODED_BLOCK_HEX.hexToBytes() else incoming.copyOf()

    private fun hasMagicBytes(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
                bytes[1] == MAGIC_BYTES[0] &&
                bytes[2] == MAGIC_BYTES[1]

    private fun insertMagic(buf: ByteArray) {
        buf[0] = 0x22.toByte()
        buf[1] = MAGIC_BYTES[0]
        buf[2] = MAGIC_BYTES[1]
    }

    private fun complete(result: String) {
        state.deferredResult?.complete(result)
        state = State()
    }

    // --- User data accessors (all remaining bytes after magic) ---

    fun setUserValue(index: Int, value: Int) {
        require(index in 0 until userDataSize) { "Index must be 0..${userDataSize - 1}" }
        currentBuf[USER_DATA_OFFSET + index] = value.toByte()
    }

    fun setAllUserValues(values: List<Int>) {
        require(values.size == userDataSize) { "Must provide exactly $userDataSize values" }
        for (i in 0 until userDataSize) {
            currentBuf[USER_DATA_OFFSET + i] = values[i].toByte()
        }
    }

    fun getUserValue(index: Int): Int {
        require(index in 0 until userDataSize)
        return currentBuf[USER_DATA_OFFSET + index].toInt()
    }

    fun getAllUserValues(): List<Int> =
        (0 until userDataSize).map { i -> currentBuf[USER_DATA_OFFSET + i].toInt() }

    // --- Persist buffer to watch ---
    fun save() {
        val finalHex = Utils.fromByteArrayToHexStr(currentBuf)
        IO.writeCmd(GetSetMode.SET, finalHex)
    }

    // --- Optional: generate random magic number (once per session) ---
    fun generateRandomMagic() {
        val r = SecureRandom()
        val newMagic = ByteArray(2)
        r.nextBytes(newMagic)
        MAGIC_BYTES = newMagic
        insertMagic(currentBuf)
    }
}
