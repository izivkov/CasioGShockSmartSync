package org.avmedia.gshockapi.casio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.utils.Utils

/**
 * The cheap, proven part of Casio's per-connection setup for the DW-H5600 family (notes, section G):
 * read `0x11` and write it back with byte index 4 bit 2 cleared; on a reconnect of the always-connect kind (9 or 10)
 * also read `0x3B` and write it back unchanged, then read `0x3A` and write it back with bytes index 7 and 8 set to
 * `DC 05`. The "settings data for this kind of connection" step is not traced and is left out.
 *
 * Driven by the frames the watch sends; one run per connection ([reset] at the start of a connection).
 * Never answers a frame it did not ask for.
 */
object ConnectionSetup {
    private enum class Step { IDLE, WAIT_KIND, WAIT_11, WAIT_3B, WAIT_3A, WAIT_23, DONE }

    private const val CODE_KIND = 0x10
    private const val CODE_BLE = 0x11
    private const val CODE_3A = 0x3A
    private const val CODE_3B = 0x3B
    private const val CODE_NAME = 0x23
    private const val CODE_CONDITION = 0x28
    private val ALWAYS_CONNECT_KINDS = setOf(9, 10) // the owner's H5600 reports 14 (2026-11-22): it gets the common steps only

    @Volatile private var step = Step.IDLE
    private var reconnectKind = false

    /** Set by the transfer code: while a LifeLog transfer runs the setup does not start. */
    @Volatile var busy: () -> Boolean = { org.avmedia.gshockapi.io.H5600HealthIO.isActive }

    /**
     * The end of Casio's setup, run on every connection (kind 14 included): the time and the DST values.
     * The default hands them to the library's own time setter.
     */
    @Volatile var timeWriter: () -> Unit = {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { org.avmedia.gshockapi.io.TimeIO.set() }
                .onFailure { ConnectionKeepAlive.log("time write failed: ${it.message}") }
        }
    }

    fun reset() {
        step = Step.WAIT_KIND
        reconnectKind = false
    }

    private fun bytes(data: String): List<Int> = runCatching { Utils.toIntArray(data) }.getOrDefault(emptyList())

    /** The `0x10` answer: byte index 7 after the code is the kind of connection. Starts the run. */
    fun onKind(data: String) {
        if (step != Step.WAIT_KIND || busy()) return
        val b = bytes(data)
        val kind = b.getOrNull(8) ?: return
        reconnectKind = kind in ALWAYS_CONNECT_KINDS
        ConnectionKeepAlive.log("Connection kind $kind: setup starts")
        step = Step.WAIT_11
        ConnectionKeepAlive.writer.request(CODE_BLE)
    }

    fun onBle(data: String) {
        if (step != Step.WAIT_11) return
        val b = bytes(data).toMutableList()
        if (b.size < 6 || b[0] != CODE_BLE) return
        b[5] = b[5] and 0x04.inv()
        ConnectionKeepAlive.writer.writeAllFeatures(b.map { it.toByte() }.toByteArray())
        ConnectionKeepAlive.log("0x11 written with bit 2 of byte 4 cleared")
        if (reconnectKind) {
            step = Step.WAIT_3B
            ConnectionKeepAlive.writer.request(CODE_3B)
        } else {
            nextName()
        }
    }

    /** Every kind: read the watch name and write it back (Casio does so every time). */
    private fun nextName() {
        step = Step.WAIT_23
        ConnectionKeepAlive.writer.request(CODE_NAME)
    }

    fun on23(data: String) {
        if (step != Step.WAIT_23) return
        val b = bytes(data)
        if (b.firstOrNull() != CODE_NAME) return
        step = Step.DONE
        ConnectionKeepAlive.writer.writeAllFeatures(b.map { it.toByte() }.toByteArray())
        ConnectionKeepAlive.log("0x23 name written back")
        ConnectionKeepAlive.writer.request(CODE_CONDITION)
        ConnectionKeepAlive.log("0x28 watch condition requested")
        timeWriter()
        ConnectionKeepAlive.log("time and DST write started")
    }

    fun on3b(data: String) {
        if (step != Step.WAIT_3B) return
        val b = bytes(data)
        if (b.firstOrNull() != CODE_3B) return
        ConnectionKeepAlive.writer.writeAllFeatures(b.map { it.toByte() }.toByteArray())
        ConnectionKeepAlive.log("0x3B written back unchanged")
        step = Step.WAIT_3A
        ConnectionKeepAlive.writer.request(CODE_3A)
    }

    fun on3a(data: String) {
        if (step != Step.WAIT_3A) return
        val b = bytes(data).toMutableList()
        if (b.size < 10 || b[0] != CODE_3A) return
        b[8] = 0xDC
        b[9] = 0x05
        ConnectionKeepAlive.writer.writeAllFeatures(b.map { it.toByte() }.toByteArray())
        ConnectionKeepAlive.log("0x3A written with bytes 7 and 8 set to DC 05")
        nextName()
    }
}
