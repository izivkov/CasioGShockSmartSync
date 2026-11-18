package org.avmedia.gshockapi.io

/** Channel a DW-H5600 notification arrived on. The watch does not report it; it is inferred from the bytes. */
enum class H5600Channel(val key: Int) {
    DRSP(0x100),
    CONVOY(0x101),
    STATUS(0x102),
}

/** A write the transfer must perform, and how long to wait before performing it. */
data class H5600Write(
    val channel: H5600Channel,
    val data: ByteArray,
    val delayMs: Long = 0L,
)

/**
 * Pure core of the DW-H5600 LifeLog transfer: given a message, decide which writes follow.
 *
 * No I/O, so the sequence can be unit-tested against recorded traffic. Captured from the official
 * app and reproduced against the watch on 2026-11-16:
 * poll until ready, two setup writes, echo the first 9-byte reply to 0x06, start on the second,
 * then echo each index announcement and finish, acknowledge each bulk fragment, and echo END.
 */
class H5600LifeLogSync {
    private enum class Phase { IDLE, POLLING, SETUP, SETUP_WAIT, TRANSFER, DONE }

    private var phase = Phase.IDLE
    private var sentSecondSetupWrite = false
    private var sixEchoCount = 0

    val isFinished: Boolean
        get() = phase == Phase.DONE

    fun start(): List<H5600Write> {
        phase = Phase.POLLING
        return listOf(poll())
    }

    fun onMessage(
        channel: H5600Channel,
        data: ByteArray,
    ): List<H5600Write> =
        when (channel) {
            H5600Channel.CONVOY -> onConvoy(data)
            H5600Channel.DRSP -> onDrsp(data)
            H5600Channel.STATUS -> emptyList()
        }

    private fun onConvoy(data: ByteArray): List<H5600Write> =
        when (phase) {
            Phase.POLLING ->
                if (data.contentEquals(READY)) {
                    phase = Phase.SETUP
                    listOf(convoy(SETUP_FIRST))
                } else {
                    // busy: the official app waits about 3.5 s before polling again
                    listOf(poll(delayMs = BUSY_RETRY_MS))
                }

            Phase.SETUP ->
                if (data.startsWith(0x04, 0x00)) {
                    if (!sentSecondSetupWrite) {
                        sentSecondSetupWrite = true
                        listOf(convoy(SETUP_SECOND))
                    } else {
                        phase = Phase.SETUP_WAIT
                        listOf(convoy(SETUP_THIRD))
                    }
                } else {
                    emptyList()
                }

            Phase.SETUP_WAIT ->
                if (data.size == NINE_BYTES && data[0] == 0x06.toByte()) {
                    // The second byte of this reply varies between sessions. Echo the first reply,
                    // then start the transfer on the second.
                    sixEchoCount++
                    if (sixEchoCount == 1) {
                        listOf(convoy(data))
                    } else {
                        phase = Phase.TRANSFER
                        listOf(H5600Write(H5600Channel.DRSP, START))
                    }
                } else {
                    emptyList()
                }

            Phase.TRANSFER ->
                if (data.firstOrNull() == BULK_PREFIX) listOf(convoy(ACK_FRAGMENT)) else emptyList()

            Phase.IDLE, Phase.DONE -> emptyList()
        }

    private fun onDrsp(data: ByteArray): List<H5600Write> {
        if (phase != Phase.TRANSFER) return emptyList()
        return when {
            data.startsWith(0x00, 0x2E) || data.startsWith(0x07, 0x2E) ->
                listOf(H5600Write(H5600Channel.DRSP, data.copyOf()))

            data.startsWith(0x04, 0x2E) -> {
                phase = Phase.DONE
                listOf(H5600Write(H5600Channel.DRSP, data.copyOf()))
            }

            else -> emptyList()
        }
    }

    companion object {
        /** Setup requests, in the order the official app sends them. Each one is answered on STATUS. */
        val SETUP_COMMANDS: List<Int> = listOf(0x23, 0x10, 0x11, 0x3B, 0x3A, 0x26, 0x28, 0x20, 0x2F, 0x43, 0x13, 0x45)

        /** Replies the official app writes back unchanged to STATUS. */
        val ECHO_REPLY_COMMANDS: Set<Int> = setOf(0x11, 0x3B, 0x3A, 0x2F, 0x13, 0x45)

        private const val BUSY_RETRY_MS = 3_500L
        private const val NINE_BYTES = 9
        private const val BULK_PREFIX: Byte = 0x05

        private val READY = byteArrayOf(0x00, 0x00, 0x00)
        private val SETUP_FIRST = ByteArray(10).also { it[0] = 0x04 }
        private val SETUP_SECOND = bytesOf(0x04, 0x01, 0x18, 0x00, 0x18, 0x00, 0x00, 0x00, 0xDC, 0x05)
        private val SETUP_THIRD = ByteArray(14).also { it[0] = 0x06 }
        private val ACK_FRAGMENT = ByteArray(15).also { it[0] = 0x07 }
        /** START on DRSP. Written before the first poll and again after the second setup reply. */
        val START = bytesOf(0x00, 0x2E, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)

        /** Which channel a notification came from, judged from its bytes. */
        fun channelOf(data: ByteArray): H5600Channel =
            when {
                data.startsWith(0x00, 0x2E) || data.startsWith(0x07, 0x2E) || data.startsWith(0x04, 0x2E) ->
                    H5600Channel.DRSP

                data.firstOrNull() == BULK_PREFIX -> H5600Channel.CONVOY
                data.contentEquals(READY) -> H5600Channel.CONVOY
                data.startsWith(0x00, 0x01) -> H5600Channel.CONVOY
                data.startsWith(0x04, 0x00) -> H5600Channel.CONVOY
                data.size == NINE_BYTES && data[0] == 0x06.toByte() -> H5600Channel.CONVOY
                else -> H5600Channel.STATUS
            }

        private fun poll(delayMs: Long = 0L) = H5600Write(H5600Channel.CONVOY, READY.copyOf(), delayMs)

        private fun convoy(data: ByteArray) = H5600Write(H5600Channel.CONVOY, data.copyOf())

        private fun bytesOf(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
    }
}
