package org.avmedia.gshockGoogleSync.scratchpad

import org.avmedia.gshockapi.IGShockAPI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
/**
 * The ScratchpadManager serves as the central coordinator for all scratchpad data operations.
 * It maintains a "master buffer" and is responsible for bit-packing/unpacking data for its clients.
 */
class ScratchpadManager @Inject constructor(
    private val api: IGShockAPI
) {
    private val clients = mutableListOf<ScratchpadClient>()

    /**
     * Literal list of allowed clients in their fixed sequential order.
     */
    private val orderedClientClasses = listOf(
        "AlarmNameStorage",
        "ActionsStorage",
        "TimeSettingsStorage"
    )

    fun register(client: ScratchpadClient) {
        val className = client.javaClass.simpleName
        if (!orderedClientClasses.contains(className)) {
            throw IllegalArgumentException("Client class '$className' not in ordered list")
        }

        if (!clients.contains(client)) {
            clients.add(client)
        }
    }

    internal suspend fun load() {
        val masterBuffer = api.getScratchpadData()

        // The watch's scratchpad is either genuinely reset, or still holds data in the
        // old (pre-0x94) magic-number layout — either way the packed bits no longer
        // match our current layout. Skip decoding entirely: every client already holds
        // its own sensible defaults from construction, so leaving them undecoded is
        // equivalent to resetting all clients to defaults. The user will simply see
        // default settings and can re-set anything they'd customized before.
        if (api.isScratchpadReset()) {
            return
        }

        var currentBitOffset = 0
        // We must iterate in the defined order to correctly calculate offsets
        orderedClientClasses.forEach { className ->
            clients.find { it.javaClass.simpleName == className }?.let { client ->
                val bitSize = client.getBitSize()
                val clientData = extractBits(masterBuffer, currentBitOffset, bitSize)
                client.decode(clientData)
                currentBitOffset += bitSize
            }
        }
    }

    internal suspend fun save() {
        val totalBits = orderedClientClasses.sumOf { className ->
            clients.find { it.javaClass.simpleName == className }?.getBitSize() ?: 0
        }
        if (totalBits == 0) return

        val masterBuffer = ByteArray(ceil(totalBits.toDouble() / 8.0).toInt())
        var currentBitOffset = 0

        orderedClientClasses.forEach { className ->
            clients.find { it.javaClass.simpleName == className }?.let { client ->
                val bitSize = client.getBitSize()
                val clientData = client.encode()
                insertBits(masterBuffer, currentBitOffset, bitSize, clientData)
                currentBitOffset += bitSize
            }
        }

        api.setScratchpadData(masterBuffer)
    }

    private fun extractBits(source: ByteArray, startBit: Int, bitCount: Int): ByteArray {
        val resultSize = ceil(bitCount.toDouble() / 8.0).toInt()
        val result = ByteArray(resultSize)

        for (i in 0 until bitCount) {
            val srcBit = startBit + i
            val srcByteIdx = srcBit / 8
            val srcBitPos = srcBit % 8

            if (srcByteIdx < source.size) {
                val bit = (source[srcByteIdx].toInt() shr srcBitPos) and 1
                if (bit == 1) {
                    val dstByteIdx = i / 8
                    val dstBitPos = i % 8
                    result[dstByteIdx] = (result[dstByteIdx].toInt() or (1 shl dstBitPos)).toByte()
                }
            }
        }
        return result
    }

    private fun insertBits(target: ByteArray, startBit: Int, bitCount: Int, source: ByteArray) {
        for (i in 0 until bitCount) {
            val srcByteIdx = i / 8
            val srcBitPos = i % 8
            val bit = (source[srcByteIdx].toInt() shr srcBitPos) and 1

            val dstBit = startBit + i
            val dstByteIdx = dstBit / 8
            val dstBitPos = dstBit % 8

            if (dstByteIdx < target.size) {
                var currentByte = target[dstByteIdx].toInt()
                if (bit == 1) {
                    currentByte = currentByte or (1 shl dstBitPos)
                } else {
                    currentByte = currentByte and (1 shl dstBitPos).inv()
                }
                target[dstByteIdx] = currentByte.toByte()
            }
        }
    }
}