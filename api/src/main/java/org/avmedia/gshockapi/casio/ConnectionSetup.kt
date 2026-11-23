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
    private enum class Step { IDLE, WAIT_KIND, WAIT_11, WAIT_3B, WAIT_3A, WAIT_23, WAIT_39, DONE }

    private const val CODE_KIND = 0x10
    private const val CODE_BLE = 0x11
    private const val CODE_3A = 0x3A
    private const val CODE_3B = 0x3B
    private const val CODE_NAME = 0x23
    private const val CODE_CONDITION = 0x28
    private const val CODE_TIME_MANAGER = 0x39
    private val ALWAYS_CONNECT_KINDS = setOf(9, 10)

    /** 13: the watch connected itself after sleep, 14: after a link loss. Casio's setup: no 0x11, 0x3A, 0x3B; the 0x39 exchange. */
    private val SELF_CONNECT_KINDS = setOf(13, 14)
    private var selfConnect = false
    private var condition: List<Int> = emptyList()

    @Volatile private var step = Step.IDLE
    private var reconnectKind = false
    private var workoutsOffered = false

    /** Called once per connection with the first `0x28` answer after the setup; the workout download starts from it. */
    @Volatile var afterSetup: (List<Int>) -> Unit = { org.avmedia.gshockapi.io.H5600WorkoutIO.onSetupDone(it) }

    /** Set by the transfer code: while a LifeLog transfer runs the setup does not start. */
    @Volatile var busy: () -> Boolean = { org.avmedia.gshockapi.io.H5600HealthIO.isActive || org.avmedia.gshockapi.io.H5600WorkoutIO.isActive }

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
        workoutsOffered = false
        selfConnect = false
        condition = emptyList()
    }

    private fun bytes(data: String): List<Int> = runCatching { Utils.toIntArray(data) }.getOrDefault(emptyList())

    /** The `0x10` answer: byte index 7 after the code is the kind of connection. Starts the run. */
    fun onKind(data: String) {
        if (step != Step.WAIT_KIND || busy()) return
        val b = bytes(data)
        val kind = b.getOrNull(8) ?: return
        reconnectKind = kind in ALWAYS_CONNECT_KINDS
        selfConnect = kind in SELF_CONNECT_KINDS
        ConnectionKeepAlive.log("Connection kind $kind: setup starts")
        if (selfConnect) {
            // No 0x11 (it holds the link-end flag), no 0x3A, no 0x3B.
            ConnectionKeepAlive.log("kind $kind: 0x11, 0x3A and 0x3B are not written")
            nextName()
            return
        }
        step = Step.WAIT_11
        ConnectionKeepAlive.writer.request(CODE_BLE)
    }

    /**
     * What Casio writes back after reading [code]: `0x11` with bit 2 of value index 4 cleared, `0x3A` with value index 7 and 8 set
     * to `DC 05`, every other value as read. Used by the connection setup and by the LifeLog setup, so both write the same bytes.
     */
    fun writeBackValue(code: Int, reply: ByteArray): ByteArray {
        val b = reply.copyOf()
        if (code == CODE_BLE && b.size >= 6) b[5] = (b[5].toInt() and 0x04.inv()).toByte()
        if (code == CODE_3A && b.size >= 10) {
            b[8] = 0xDC.toByte()
            b[9] = 0x05
        }
        return b
    }

    fun onBle(data: String) {
        if (step != Step.WAIT_11) return
        val b = bytes(data)
        if (b.size < 6 || b[0] != CODE_BLE) return
        ConnectionKeepAlive.writer.writeAllFeatures(writeBackValue(CODE_BLE, b.map { it.toByte() }.toByteArray()))
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
        ConnectionKeepAlive.writer.writeAllFeatures(b.map { it.toByte() }.toByteArray())
        ConnectionKeepAlive.log("0x23 name written back")
        if (selfConnect) {
            step = Step.WAIT_39
            for (code in listOf(0x26, CODE_CONDITION, 0x20, CODE_CONDITION, CODE_TIME_MANAGER)) {
                ConnectionKeepAlive.writer.request(code)
            }
            ConnectionKeepAlive.log("read 0x26, 0x28, 0x20, 0x28, 0x39; waiting for the 0x39 value")
            return
        }
        step = Step.DONE
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
        val b = bytes(data)
        if (b.size < 10 || b[0] != CODE_3A) return
        ConnectionKeepAlive.writer.writeAllFeatures(writeBackValue(CODE_3A, b.map { it.toByte() }.toByteArray()))
        ConnectionKeepAlive.log("0x3A written with bytes 7 and 8 set to DC 05")
        nextName()
    }

    /** The `0x28` answer: kept for the `0x39` decision; "index 7" counts after the code, like `0x10` and `0x11`. */
    fun on28(data: String) {
        if (step == Step.DONE && !workoutsOffered) {
            val c = bytes(data)
            if (c.firstOrNull() == CODE_CONDITION) {
                workoutsOffered = true
                afterSetup(c)
            }
        }
        if (!selfConnect) return
        condition = bytes(data)
        ConnectionKeepAlive.log("0x28 received: " + condition.take(12).joinToString(" ") { "%02X".format(it) })
    }

    private fun timeChangeAllowed(): Boolean {
        val v = condition.getOrNull(8) ?: return true.also { ConnectionKeepAlive.log("0x28 unknown: time change taken as allowed") }
        return v and 0x04 == 0
    }

    private fun timeUpdate() {
        ConnectionKeepAlive.log("leap-second write 25 18 18 17 20 01 01 skipped (values not confirmed)")
        timeWriter()
        ConnectionKeepAlive.writer.request(CODE_CONDITION)
        ConnectionKeepAlive.log("time update: time and DST write started, 0x28 requested")
    }

    /** The watch's `39` value (read at setup, and whenever it notifies again). Kinds 13 and 14 only. */
    fun on39(data: String) {
        if (!selfConnect || busy()) return
        val value = bytes(data).getOrNull(1) ?: return
        ConnectionKeepAlive.log("39 %02X received".format(value))
        when (value) {
            0 -> if (timeChangeAllowed()) answer(3) else answer(5)
            2 -> timeUpdate().also { step = Step.DONE }
            4 -> answer(4) // time zone, DST and home city are taken as equal: nothing to do
            5 -> ConnectionKeepAlive.log("39 05: the watch aborted (ignored)")
            else -> ConnectionKeepAlive.log("39 %02X: not handled".format(value))
        }
    }

    private fun answer(value: Int) {
        ConnectionKeepAlive.writer.writeAllFeatures(byteArrayOf(CODE_TIME_MANAGER.toByte(), value.toByte()))
        ConnectionKeepAlive.log("39 %02X written".format(value))
        if (value != 3) {
            step = Step.DONE
            if (!workoutsOffered && condition.isNotEmpty()) {
                workoutsOffered = true
                afterSetup(condition)
            }
        }
    }
}
