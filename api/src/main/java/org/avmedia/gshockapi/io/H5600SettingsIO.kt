package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.model.SmartAlarm
import org.avmedia.gshockapi.model.SoundMode
import org.avmedia.gshockapi.model.WatchFaceItem
import org.avmedia.gshockapi.protocols.H5600Protocol
import timber.log.Timber

/**
 * DW-H5600 (module 3516 family) settings decoded from the official app's BLE traffic.
 * See docs/DW-H5600-protocol.md and FINDINGS.md in the h5600-probe folder for the raw captures.
 *
 * 0x42 (6 bytes): [0]=0x42, [1]=sound mode, [2..5]=per-function CUSTOM table (kept as-is).
 * 0x50 (5 bytes): smart alarm: hh mm flags(01 on, 02 snooze) window(min).
 * 0x51 (6 bytes): [0]=0x51, [1]=face (1 weekday, 2 custom), [2..5]=display item (all four set).
 *
 * All reads go through [RawSettingIO] (shared requests, retries); all writes are read-modify-write
 * through [RawSettingIO.modify], so they keep unknown bytes and cannot undo each other.
 */
@RequiresApi(Build.VERSION_CODES.O)
object H5600SettingsIO {
    const val SOUND_MODE_CODE = 0x42
    const val WATCH_FACE_CODE = 0x51
    const val SMART_ALARM_CODE = 0x50

    /** CASIO_CURRENT_TIME_MANAGER. The official app requests it after connecting and writes the reply back. */
    const val TIME_MANAGER_CODE = 0x39

    private const val PACKET_SIZE = 6
    private const val SMART_ALARM_SIZE = 5

    @RequiresApi(Build.VERSION_CODES.O)
    fun isSupported(): Boolean = WatchInfo.protocol is H5600Protocol

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkSupported() = check(isSupported()) { "Not an H5600 protocol watch (protocol=${WatchInfo.protocol})" }

    private fun checkPacket(
        code: Int,
        packet: IntArray,
        size: Int = PACKET_SIZE,
    ) = check(packet.size == size && packet[0] == code) { "Unexpected 0x%02X packet: %s".format(code, packet.toHex()) }

    private suspend fun readChecked(
        code: Int,
        size: Int = PACKET_SIZE,
    ): IntArray {
        checkSupported()
        return RawSettingIO.request(code).also { checkPacket(code, it, size) }
    }

    /** Checked read-modify-write; returns the packet before the change. */
    private suspend fun modifyChecked(
        code: Int,
        size: Int = PACKET_SIZE,
        transform: (IntArray) -> IntArray?,
    ): IntArray {
        checkSupported()
        return RawSettingIO.modify(code) { p ->
            checkPacket(code, p, size)
            transform(p)
        }
    }

    // ---- Connection ----

    /**
     * Connection handshake copied from the official CASIO app (capture 2026-11-16 17:57:06):
     *   app -> 39 (request), watch -> 39 04, app -> 39 04 (written back unchanged), watch -> 47 01.
     * Without it the watch drops Smart Sync's connection after ~45 s; with the official app it stays
     * connected. Echoes exactly what the watch sent; nothing else is written.
     */
    suspend fun connectionHandshake() {
        if (!isSupported()) return
        val reply = RawSettingIO.request(TIME_MANAGER_CODE)
        check(reply.isNotEmpty() && reply[0] == TIME_MANAGER_CODE && reply.size <= 4) { "Unexpected 0x39 reply: ${reply.toHex()}" }
        RawSettingIO.write(reply)
    }

    /** Read-only: reads packets the official app reads on every connect, for logging/comparison. */
    suspend fun readDiagnostics(): Map<Int, String> =
        listOf(0x45, 0x4F).associateWith { code ->
            runCatching { RawSettingIO.request(code).toHex() }.getOrElse { "error: ${it.message}" }
        }

    // ---- Home location (0x24) ----

    /**
     * Home location for sunrise/sunset: `24 00 01 <lat f64 BE> <lon f64 BE> 04`. Trailing byte 0x04
     * as the current official app writes it (user's capture 2026-11-16 23:08; izivkov's older capture
     * had 0x01). The watch computes sunrise/sunset from it; without it the watch shows dashes.
     * Last location given by the app; used when [homeLocationProvider] has none.
     */
    @Volatile
    private var homeLocation: Pair<Double, Double>? = null

    private fun isValid(
        lat: Double,
        lon: Double,
    ) = lat in -90.0..90.0 && lon in -180.0..180.0

    fun rememberHomeLocation(
        latitude: Double,
        longitude: Double,
    ) {
        if (isValid(latitude, longitude)) homeLocation = latitude to longitude
    }

    /**
     * Supplied by the app: the location to send right now (real or "weather" coordinates). Asked at
     * time-sync time so a sync never goes out without a location, even before the app has
     * remembered one in this process.
     */
    @Volatile
    var homeLocationProvider: (() -> Pair<Double, Double>?)? = null

    /**
     * Called by TimeIO in the middle of a time sync, after the DST/world-city writes (1D, 1E) and
     * before the city names (1F) and the time (09): the exact place the official app writes 0x24,
     * so the watch recalculates sunrise/sunset with the location already in place.
     * Never throws: a bad or missing location must not stop the time from being set.
     */
    fun writeLocationInTimeSync() {
        if (!isSupported()) return
        val loc =
            runCatching { homeLocationProvider?.invoke() }.getOrNull()?.takeIf { isValid(it.first, it.second) }
                ?: homeLocation ?: return
        runCatching { setHomeLocation(loc.first, loc.second) }.onFailure { Timber.e(it, "0x24 location write failed") }
    }

    fun setHomeLocation(
        latitude: Double,
        longitude: Double,
    ) {
        checkSupported()
        require(isValid(latitude, longitude)) { "Bad coordinates" }
        homeLocation = latitude to longitude
        val buf = java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.put(0x24.toByte()).put(0x00).put(0x01).putDouble(latitude).putDouble(longitude).put(0x04)
        RawSettingIO.write(buf.array().map { it.toInt() and 0xFF }.toIntArray())
    }

    // ---- Sound mode (0x42) ----

    /** Raw sound packet as hex, for mapping the per-function CUSTOM bits. */
    suspend fun getSoundPacketHex(): String = readChecked(SOUND_MODE_CODE).toHex()

    suspend fun getSoundMode(): SoundMode? = SoundMode.fromCode(readChecked(SOUND_MODE_CODE)[1])

    /** Sets the mode (no write if already equal). Returns the mode the watch had before. */
    suspend fun setSoundMode(mode: SoundMode): SoundMode? =
        modifyChecked(SOUND_MODE_CODE) { p -> p.also { it[1] = mode.code } }
            .let { SoundMode.fromCode(it[1]) }
            .also { if (it == mode) Timber.i("Sound mode already $mode, not writing") }

    // ---- Watch face (0x51) ----

    /** Returns null if the four item bytes disagree or hold an unknown code. */
    suspend fun getWatchFaceItem(): WatchFaceItem? = getWatchFaceCode()?.let { WatchFaceItem.fromCode(it) }

    /** Raw display-item code (also for values not yet in [WatchFaceItem]); null if the four bytes disagree. */
    suspend fun getWatchFaceCode(): Int? = itemCode(readChecked(WATCH_FACE_CODE))

    private fun itemCode(p: IntArray): Int? = p.copyOfRange(2, PACKET_SIZE).distinct().singleOrNull()

    /** Watch face: byte 1 of 0x51, 1 = simple face (weekday), 2 = customisable face (item). */
    suspend fun getWatchFaceNumber(): Int = readChecked(WATCH_FACE_CODE)[1]

    suspend fun setWatchFaceNumber(face: Int) {
        require(face == 1 || face == 2) { "face must be 1 or 2" }
        modifyChecked(WATCH_FACE_CODE) { p -> p.also { it[1] = face } }
    }

    /** Sets the display item (no write if already equal). Returns the item the watch had before. */
    suspend fun setWatchFaceItem(item: WatchFaceItem): WatchFaceItem? =
        modifyChecked(WATCH_FACE_CODE) { p -> p.also { for (i in 2 until PACKET_SIZE) it[i] = item.code } }
            .let { before -> itemCode(before)?.let { WatchFaceItem.fromCode(it) } }

    // ---- Smart alarm (0x50) ----

    suspend fun getSmartAlarm(): SmartAlarm =
        readChecked(SMART_ALARM_CODE, SMART_ALARM_SIZE).let { p ->
            SmartAlarm(p[1], p[2], p[3] and 0x01 != 0, p[3] and 0x02 != 0, p[4])
        }

    /** Writes the smart-alarm slot; other flag bits read from the watch are kept. */
    suspend fun setSmartAlarm(a: SmartAlarm) {
        require(a.hour in 0..23 && a.minute in 0..59 && a.windowMin in 0..120) { "Bad smart alarm" }
        modifyChecked(SMART_ALARM_CODE, SMART_ALARM_SIZE) { p ->
            var flags = p[3] and 0x03.inv()
            if (a.enabled) flags = flags or 0x01
            if (a.snooze) flags = flags or 0x02
            intArrayOf(SMART_ALARM_CODE, a.hour, a.minute, flags, a.windowMin)
        }
    }

    // ---- Basic settings restore (0x13) ----
}
