package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.CasioTimeZoneHelper
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles time synchronization for the Casio GW-BX5600 watch model family (QW3575 module series).
 *
 * ## Protocol Summary
 * 1. **Step 1 (Time-Slot Data):** Sends request `0x05` over `SP_REQUEST`, receives 101-byte block
 *    via `SP_DATA`, modifies header to `0x02`, and writes back to `SP_DATA`.
 * 2. **Step 2 (World-City Data):** Sends request `0x03` over `SP_REQUEST`, receives 28-byte block
 *    via `SP_DATA`, changes header to `0x06`, appends three 22-byte city location records (94B total),
 *    and writes to `SP_DATA`.
 * 3. **Step 3 (City Names):** Sends request `0x06` over `SP_REQUEST`, receives 133-byte block
 *    via `SP_DATA`, and echoes it back.
 * 4. **Step 4 (Time Write):** Writes 11-byte `CLASS_C_TIME_CURRENT_TIME` command (`0x09` header) to `SET`.
 */
@RequiresApi(Build.VERSION_CODES.O)
object GwBx5600TimeIO {

    // Default constants for empty/unpopulated world city slots (Slots 1 & 2)
    private const val CITY_RECORD_FLAG: Byte = 0x01
    private const val EMPTY_SLOT_TRAILING: Byte = 0x00
    private const val EMPTY_SLOT_LAT = 0.0
    private const val EMPTY_SLOT_LON = 0.0

    /**
     * Manages asynchronous multi-packet response accumulation for a single protocol step.
     */
    private class StepChannel {
        var expectedBytes: Int = 0
        var accumulator = ByteArray(0)
        var result: CompletableDeferred<ByteArray>? = null
    }

    private val step1Channel = StepChannel()
    private val step2Channel = StepChannel()
    private val step3Channel = StepChannel()

    /**
     * Synchronizes the watch's current time and world city settings.
     *
     * @param timeMs Epoch timestamp in milliseconds. Defaults to system current time.
     */
    suspend fun set(timeMs: Long? = null) {
        val nowMs = timeMs ?: Clock.systemDefaultZone().millis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())

        Timber.i("GwBx5600TimeIO.set: $now")

        // ---------------------------------------------------------------------
        // Step 1/4: Time-Slot Data & DST Watch State Block Query (0x05 -> 0x02)
        // ---------------------------------------------------------------------
        Timber.i("Step 1/4: time-slot data")
        var req1 = byteArrayOf(0x05)
        req1 += byteArrayOf(0x1D, 0x00, 0x1D, 0x00) // Query DST Watch State blocks 0 & 1
        req1 += byteArrayOf(0x24, 0x00, 0x24, 0x01, 0x24, 0x02) // Query Time Slot blocks 0, 1, & 2

        val notif1 = request(step1Channel, req1, expected = 101)
        if (notif1 != null) {
            val wb1 = notif1.copyOf()
            wb1[0] = 0x02 // Command byte modification: Read (0x05) -> Write (0x02)
            Timber.d("GwBx5600TimeIO Step1 write: ${wb1.size}B ${wb1.toHexString()}")
            Connection.write(GetSetMode.SP_DATA, wb1)
        } else {
            Timber.w("GwBx5600TimeIO Step1: no response from watch (timed out)")
            return
        }

        // ---------------------------------------------------------------------
        // Step 2/4: World-City Coordinates & DST Setting Query (0x03 -> 0x06)
        // ---------------------------------------------------------------------
        Timber.i("Step 2/4: world-city data")
        var req2 = byteArrayOf(0x03)
        val blocks = ceil(WatchInfo.worldCitiesCount / 2.0).toInt()
        for (i in 0 until blocks) {
            req2 += byteArrayOf(CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code.toByte(), 0x00)
        }

        val notif2 = request(step2Channel, req2, expected = 28)
        if (notif2 != null) {
            val wb2 = notif2.copyOf()
            wb2[0] = 0x06 // Command byte modification: Read (0x03) -> Write (0x06)
            val withCityData = wb2 + buildWorldCityRecords()
            Timber.d("GwBx5600TimeIO Step2 write: ${withCityData.size}B (expect 94) ${withCityData.toHexString()}")
            Connection.write(GetSetMode.SP_DATA, withCityData)
        } else {
            Timber.w("GwBx5600TimeIO Step2: no response from watch (timed out)")
            return
        }

        // ---------------------------------------------------------------------
        // Step 3/4: World City Names Echo (0x06 -> 0x06)
        // ---------------------------------------------------------------------
        Timber.i("Step 3/4: city names")
        var req3 = byteArrayOf(0x06)
        for (i in 0 until WatchInfo.worldCitiesCount) {
            val idx = (i / 2) + if (i % 2 != 0) 6 else 0
            req3 += byteArrayOf(CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code.toByte(), idx.toByte())
        }

        val notif3 = request(step3Channel, req3, expected = 1 + (WatchInfo.worldCitiesCount * 22))
        if (notif3 != null) {
            Timber.d("GwBx5600TimeIO Step3 write: ${notif3.size}B ${notif3.toHexString()}")
            Connection.write(GetSetMode.SP_DATA, notif3)
        } else {
            Timber.w("GwBx5600TimeIO Step3: no response from watch (timed out)")
            return
        }

        // ---------------------------------------------------------------------
        // Step 4/4: Write Current Time Command (CLASS_C_TIME_CURRENT_TIME 0x09)
        // ---------------------------------------------------------------------
        writeTimeCommand(now)
        Timber.i("GwBx5600TimeIO.set: complete")
    }

    /**
     * Constructs the 66-byte block (three 22-byte city location records) required by Step 2.
     * Slot 0 carries Home City coordinates and DST status; Slots 1 and 2 are filled with empty defaults.
     */
    private fun buildWorldCityRecords(): ByteArray {
        val casioTimezone = TimeIO.getCasioTimezone()
        val (lat, lon, _) = CasioTimeZoneHelper.getWorldCityCoordinates(casioTimezone.zoneId)
        val dstValue = if (casioTimezone.isInDST()) 1 else 0

        val homeRecord = cityRecord(slotIndex = 0, lat = lat, lon = lon, trailing = dstValue.toByte())
        val emptySlot1 = cityRecord(slotIndex = 1, lat = EMPTY_SLOT_LAT, lon = EMPTY_SLOT_LON, trailing = EMPTY_SLOT_TRAILING)
        val emptySlot2 = cityRecord(slotIndex = 2, lat = EMPTY_SLOT_LAT, lon = EMPTY_SLOT_LON, trailing = EMPTY_SLOT_TRAILING)

        return homeRecord + emptySlot1 + emptySlot2
    }

    /**
     * Encodes a single 22-byte city location record.
     *
     * Format:
     * - `[0x14, 0x00]`: Length (LE 0x0014 = 20 payload bytes follow)
     * - `[0x24]`: Tag (Time Slot)
     * - `[slotIndex]`: 0, 1, or 2
     * - `[CITY_RECORD_FLAG]`: 0x01
     * - `[lat]`: 64-bit Double (Big Endian)
     * - `[lon]`: 64-bit Double (Big Endian)
     * - `[trailing]`: DST boolean byte (0x01 or 0x00)
     */
    private fun cityRecord(slotIndex: Int, lat: Double, lon: Double, trailing: Byte): ByteArray {
        val buf = ByteBuffer.allocate(22).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x14) // Length low byte
        buf.put(0x00) // Length high byte
        buf.put(0x24) // Record Tag
        buf.put(slotIndex.toByte())
        buf.put(CITY_RECORD_FLAG)
        buf.putDouble(lat)
        buf.putDouble(lon)
        buf.put(trailing)
        return buf.array()
    }

    /**
     * Issues a request over `SP_REQUEST` and suspends until the target byte count is received.
     */
    private suspend fun request(channel: StepChannel, reqPayload: ByteArray, expected: Int): ByteArray? {
        val deferred = CompletableDeferred<ByteArray>()
        synchronized(channel) {
            channel.expectedBytes = expected
            channel.accumulator = ByteArray(0)
            channel.result = deferred
        }
        try {
            Connection.write(GetSetMode.SP_REQUEST, reqPayload)
            return withTimeoutOrNull(5000L.milliseconds) {
                deferred.await()
            }
        } finally {
            synchronized(channel) {
                channel.result = null
                channel.accumulator = ByteArray(0)
                channel.expectedBytes = 0
            }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

    /**
     * Sends the 11-byte Current Time payload over the `SET` (`CASIO_ALL_FEATURES`) characteristic.
     *
     * Payload Structure:
     * - `0x09`: Class ID (`CLASS_C_TIME_CURRENT_TIME`)
     * - `[Year Low, Year High]`: 16-bit Year (Little-Endian)
     * - `[Month, Day, Hour, Minute, Second]`: 8-bit integers
     * - `[Day of Week]`: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
     * - `[Fractions]`: 256ths of a second
     * - `0x01`: Adjust Reason (Manual Update)
     */
    private fun writeTimeCommand(now: LocalDateTime) {
        // CASIO CTS Day-of-Week encoding: 1=Mon, 2=Tue, ..., 7=Sun
        val casioDow = if (now.dayOfWeek == DayOfWeek.SUNDAY) 7 else now.dayOfWeek.value
        val subSecondByte = ((now.nano.toLong() * 256) / 1_000_000_000).toByte()

        val timeCmd = byteArrayOf(
            0x09, // CLASS_C_TIME_CURRENT_TIME multiplexer header
            (now.year and 0xFF).toByte(),
            ((now.year shr 8) and 0xFF).toByte(),
            now.monthValue.toByte(),
            now.dayOfMonth.toByte(),
            now.hour.toByte(),
            now.minute.toByte(),
            now.second.toByte(),
            casioDow.toByte(),
            subSecondByte,
            0x01 // Adjust Reason: Manual Update (App)
        )
        val hexStr = timeCmd.toHexString()
        Timber.i("Step 4/4: time command: $hexStr")
        Connection.write(GetSetMode.SET, timeCmd)
    }

    /**
     * Receives notification fragments, accumulates incoming bytes, and triggers channel completion.
     */
    private fun onReceivedFor(channel: StepChannel, label: String, data: String) {
        val deferred = synchronized(channel) { channel.result } ?: return

        val ints = org.avmedia.gshockapi.utils.Utils.toIntArray(data)
        val bytes = ByteArray(ints.size) { i -> ints[i].toByte() }

        val (accumulated, expected) = synchronized(channel) {
            channel.accumulator += bytes
            channel.accumulator.size to channel.expectedBytes
        }

        Timber.d("GwBx5600TimeIO.onReceived[$label]: accumulated=${accumulated}B / expected=${expected}B")

        if (accumulated >= expected) {
            synchronized(channel) {
                deferred.complete(channel.accumulator)
            }
        }
    }

    fun onReceivedStep1(data: String) = onReceivedFor(step1Channel, "step1", data)
    fun onReceivedStep2(data: String) = onReceivedFor(step2Channel, "step2", data)
    fun onReceivedStep3(data: String) = onReceivedFor(step3Channel, "step3", data)
}
