package org.avmedia.gshockapi.casio

import java.io.File
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.log.LogSource

/** Link Loss is written as `00 level` to the all-features characteristic, never to the standard Link Loss characteristic. */
fun linkLossFrame(level: Int): ByteArray = byteArrayOf(0x00, level.toByte())

/** The writes the keep-alive and the connection setup make. A fake in the unit tests. */
interface WatchWriter {
    /** A write with response to the all-features characteristic (what Casio's app uses for `49 01`, `0x11`, `0x3A`). */
    fun writeAllFeatures(bytes: ByteArray)

    /** A read request (one code) on the request characteristic. */
    fun request(code: Int)

    /** Link Loss alert level (0 none, 1 mild, 2 high): the frame `00 level` on the all-features characteristic. */
    fun writeLinkLoss(level: Int)
}

internal object ConnectionWriter : WatchWriter {
    override fun writeAllFeatures(bytes: ByteArray) = WatchLink.write(GetSetMode.SET, bytes)

    override fun request(code: Int) = WatchLink.write(GetSetMode.GET, byteArrayOf(code.toByte()))

    override fun writeLinkLoss(level: Int) = WatchLink.write(GetSetMode.SET, linkLossFrame(level))
}

/**
 * The connection upkeep of the DW-H5600 family, as Casio's app does it: the watch notifies `49 00` (connection check)
 * and the app answers at once with `49 01`; the watch notifies `47 01` when its setup is done and `47 02` to ask for the
 * Link Loss alert level 1. Every such notification is logged with its time so a health-log share shows whether the
 * drops stop. Pure apart from the [writer].
 */
object ConnectionKeepAlive {
    const val CHECK = 0x49
    const val SETUP = 0x47

    @Volatile var writer: WatchWriter = ConnectionWriter

    /** Called on `47 01` (the watch's setup is done): the app writes what was waiting for it. */
    @Volatile var onSetupDone: () -> Unit = {}

    /** Where the events are also kept (a file in the health-log folder), null keeps them in memory only. */
    var logFile: File?
        get() = AppLog.logFile
        set(v) { AppLog.logFile = v }

    var clock: () -> Long
        get() = AppLog.clock
        set(v) { AppLog.clock = v }

    fun recentEvents(): List<String> = AppLog.recentEvents()

    fun clearEvents() = AppLog.clear()

    /** The watch's connection events go to the one app log, source Watch. */
    fun log(text: String) = AppLog.log(LogSource.Watch, text)

    private fun values(data: String): List<Int> = runCatching { Utils.toIntArray(data) }.getOrDefault(emptyList())

    /** `49 00`: write `49 01` once. Any other value is only logged. */
    fun onCheck(data: String) {
        val v = values(data)
        if (v.getOrNull(1) == 0) {
            writer.writeAllFeatures(byteArrayOf(0x49, 0x01))
            log("49 00 received, 49 01 written")
        } else {
            log("49 ${v.getOrNull(1)?.let { "%02X".format(it) } ?: "?"} received (not answered)")
        }
    }

    /** `47 01`: setup done. `47 02`: write the Link Loss alert level 1. */
    fun onSetup(data: String) {
        when (val value = values(data).getOrNull(1)) {
            1 -> {
                log("47 01 received: the watch's setup is done")
                runCatching { onSetupDone() }
            }
            2 -> {
                writer.writeLinkLoss(1)
                log("47 02 received, Link Loss alert level 1 written")
            }
            else -> log("47 ${value?.let { "%02X".format(it) } ?: "?"} received (ignored)")
        }
    }
}
