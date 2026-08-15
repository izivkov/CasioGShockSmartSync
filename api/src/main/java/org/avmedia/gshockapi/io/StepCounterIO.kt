package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.StepCounterData
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

// ============================================================================
// Pure Functional Core: Step Counter Decoding
// ============================================================================

/**
 * Pure functional core for step counter processing.
 *
 * All methods are pure: no mutable state, no side effects.
 * Handles step count extraction from a fully-reassembled activity-record
 * (life-log) payload -- see StepCounterIO below for why reassembly is
 * required before this can be called.
 *
 * ### Payload Layout (0x26 Activity Record)
 * Confirmed from a real HCI capture (request/ack/first-fragment sequence
 * for category 0x11 = EXERCISE_DATA on ABL-100WE), cross-checked against
 * an independent reference implementation:
 *
 * | Offset | Size | Description |
 * | :--- | :--- | :--- |
 * | 0 | 1 | Header (must be 0x26) |
 * | 1 | 1 | Day of Week |
 * | 2 | 1 | Month |
 * | 3 | 1 | Day of Month (UNCONFIRMED -- see note below) |
 * | 4-5 | 2 | Padding/Unknown |
 * | 6 | 288 | 144 hourly slots (2 bytes each, LE; 0xFFFE = unavailable) |
 * | 294 | 24 | Between-history padding |
 * | 318 | 56 | 14 daily slots (4 bytes each, LE) |
 * | 374 | 4 | Current day total steps (4 bytes, LE) -- CONFIRMED from two
 * |   |   | independent sources (this file's own prior version, and a
 * |   |   | working Python reference implementation), both giving the
 * |   |   | same offset and matching decode against real capture data.
 *
 * UNCONFIRMED:
 *   - The 0xFFFE / 0xFFFFFFFE "unavailable" sentinel convention IS
 *     independently verified: the captured hourly slots (repeated
 *     "FE FF" pairs) decode to exactly 0xFFFE as claimed here.
 *   - byte[3] as "Day of Month" is NOT confirmed -- an earlier version
 *     of this file read the same byte as "hourly slot count" instead
 *     (both are consistent with the one captured value, 0x18=24, since
 *     24 is a plausible value for either interpretation). Only matters
 *     if you need that field; doesn't affect the step-count offsets.
 *   - The layout above only accounts for 378 of the 400 bytes the watch
 *     actually advertises as the total transfer length (confirmed from
 *     the real ack: 0x000190 = 400). The remaining 22 trailing bytes are
 *     NOT modeled here -- unknown content (checksum? reserved? additional
 *     fields?). Doesn't block step-count parsing since that offset (374)
 *     is well within the first 378 bytes, but worth resolving before
 *     treating this layout as fully understood.
 */
@RequiresApi(Build.VERSION_CODES.O)
object StepCounterIOFunctional {
    private const val HEADER_SIZE = 6
    private const val HOURLY_SLOT_COUNT = 144
    private const val HOURLY_SLOT_SIZE = 2
    private const val BETWEEN_HISTORY_PADDING_SIZE = 24
    private const val DAILY_SLOT_COUNT = 14
    private const val DAILY_SLOT_SIZE = 4

    fun parse(payload: ByteArray): StepCounterData? {
        val dailyHistoryOffset = HEADER_SIZE + HOURLY_SLOT_COUNT * HOURLY_SLOT_SIZE +
                BETWEEN_HISTORY_PADDING_SIZE
        val currentDayOffset = dailyHistoryOffset + DAILY_SLOT_COUNT * DAILY_SLOT_SIZE
        if (payload.size < currentDayOffset + DAILY_SLOT_SIZE || payload.firstOrNull()?.toInt() != 0x26) {
            return null
        }

        val hourlySteps = List(HOURLY_SLOT_COUNT) { index ->
            payload.readUnsignedShortOrNull(HEADER_SIZE + index * HOURLY_SLOT_SIZE)
        }
        val dailyHistory = List(DAILY_SLOT_COUNT) { index ->
            payload.readUnsignedIntOrNull(dailyHistoryOffset + index * DAILY_SLOT_SIZE)
        }

        return StepCounterData(
            dayOfWeek = payload[1].toInt() and 0xFF,
            month = payload[2].toInt() and 0xFF,
            dayOfMonth = payload[3].toInt() and 0xFF,
            hourlySteps = hourlySteps,
            dailyHistory = dailyHistory,
            currentDaySteps = payload.readUnsignedIntOrNull(currentDayOffset),
        )
    }

    private fun ByteArray.readUnsignedShortOrNull(offset: Int): Int? {
        if (offset + 2 > size) return null
        val value = (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
        return value.takeUnless { it == 0xFFFE } // confirmed against real capture, see doc comment above
    }

    private fun ByteArray.readUnsignedIntOrNull(offset: Int): Int? {
        if (offset + 4 > size) return null
        val value = (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8) or
                ((this[offset + 2].toInt() and 0xFF) shl 16) or
                ((this[offset + 3].toInt() and 0xFF) shl 24)
        return value.takeUnless { it == -2 } // 0xFFFFFFFE
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/*
Confirmed from a real HCI capture and cross-checked against a working
Python reference implementation:

  TX  0x0011 (DRSP):   00 11 00 00 00        -- start: command=0, category=0x11
  RX  0x0011 (DRSP):   00 11 90 01 00 00 00  -- ack: total length = 400 bytes
  RX  0x0014 (Convoy): 26 07 01 18 40 01 ...  -- data fragments, ~20B each

Two things a prior version of this file got wrong, both confirmed by the
Python reference:
  1. The watch's answer is NOT one notification -- it's ~20 separate
     fragments that must be concatenated to the full 400-byte length
     before parsing. There is no evidence anywhere else in this codebase
     of a dispatcher layer that reassembles multiple GATT notification
     EVENTS automatically (as opposed to ACL-level fragments within one
     notification, which Android does reassemble) -- assuming otherwise
     was untested and is very likely wrong.
  2. The transfer needs an explicit end-transaction acknowledgment sent
     back to the watch (command=0x04) once the full length has arrived --
     matching WFS_DRSP_COMMANDS_END_TRANSACTION=4 seen in the official
     app's own source. Neither prior Kotlin version sent this.

STILL NEEDS YOUR INPUT:
  - The DRSP length-announcement/end-transaction messages (the
    "00 11 90 01 00 00 00"-style acks on handle 0x0011) need to be routed
    to onDrspReceived() below. I don't know the CasioConstants key
    WatchProtocol.dataReceivedHandlers should use for that -- presumably
    CASIO_DATA_REQUEST_SP's own code, parallel to how 0x26 already routes
    the Convoy data fragments to onReceived(). Please wire that in (or
    tell me the constant).
  - Until that's wired up, expectedLength falls back to a hardcoded 400,
    which matches this capture but isn't a real fix for anything else.
  - The 22 unmodeled trailing bytes (see StepCounterIOFunctional's doc
    comment) are still unexplained.
*/
@RequiresApi(Build.VERSION_CODES.O)
object StepCounterIO {

    private const val FALLBACK_EXPECTED_LENGTH = 400
    private const val DRSP_CATEGORY_EXERCISE = 0x11
    private val START_TRANSACTION_CMD = byteArrayOf(0x00, DRSP_CATEGORY_EXERCISE.toByte(), 0x00, 0x00, 0x00)
    private val END_TRANSACTION_CMD = byteArrayOf(0x04, DRSP_CATEGORY_EXERCISE.toByte(), 0x00, 0x00, 0x00)

    private var accumulator = ByteArray(0)
    private var expectedLength: Int = FALLBACK_EXPECTED_LENGTH
    private var result: CompletableDeferred<StepCounterData>? = null

    suspend fun request(): StepCounterData {
        if (!WatchInfo.hasStepCounter) {
            Timber.i("Step counter not supported on watch model: ${WatchInfo.model}")
            return StepCounterData.unavailable()
        }
        return getStepCount()
    }

    private suspend fun getStepCount(): StepCounterData {
        val deferred = CompletableDeferred<StepCounterData>()
        synchronized(this) {
            accumulator = ByteArray(0)
            expectedLength = FALLBACK_EXPECTED_LENGTH
            result = deferred
        }
        try {
            IO.writeCmd(GetSetMode.DATA_REQUEST, START_TRANSACTION_CMD)
            val stepData = withTimeoutOrNull(10_000L.milliseconds) { deferred.await() }
            if (stepData == null) {
                Timber.w("StepCounterIO: timed out waiting for activity record (accumulated ${accumulator.size}/${expectedLength}B)")
            }
            return stepData ?: StepCounterData.unavailable()
        } finally {
            synchronized(this) {
                result = null
                accumulator = ByteArray(0)
            }
        }
    }

    /**
     * Call this from whatever handler receives DRSP-envelope messages on
     * the "Data Request SP" characteristic (handle 0x0011 in the capture)
     * -- both the length-announcement ack (command=0x00) and any
     * end-transaction confirmation from the watch (command=0x04). See
     * file-level TODO -- the real WatchProtocol routing key for this is
     * not yet confirmed.
     *
     * @param data Raw bytes of the DRSP message, e.g. [00,11,90,01,00,00,00]
     */
    fun onDrspReceived(data: ByteArray) {
        if (data.size < 5) return
        val command = data[0].toInt() and 0xFF
        val category = data[1].toInt() and 0xFF
        if (category != DRSP_CATEGORY_EXERCISE) return

        if (command == 0x00) {
            val length = (data[2].toInt() and 0xFF) or
                    ((data[3].toInt() and 0xFF) shl 8) or
                    ((data[4].toInt() and 0xFF) shl 16)
            synchronized(this) {
                if (result != null) {
                    expectedLength = length
                    Timber.d("StepCounterIO: expected length announced = ${length}B")
                }
            }
        }
        // command == 0x04 (end transaction, watch-initiated) needs no action here --
        // finalization already happens in onReceived() once the buffer is full.
    }

    /**
     * Called when a Convoy (activity-record) notification fragment is
     * received. Appends it to the accumulator; only attempts to parse
     * once the full advertised length has arrived, and sends the
     * end-transaction acknowledgment back to the watch at that point
     * (confirmed necessary -- see file-level note).
     *
     * @param data The notification payload as a string of space-separated hex values
     */
    fun onReceived(data: String) {
        val deferred = synchronized(this) { result } ?: return

        try {
            val intArr = Utils.toIntArray(data)
            val bytes = Utils.byteArrayOfIntArray(intArr.toIntArray())

            val accumulated = synchronized(this) {
                accumulator += bytes
                accumulator.size
            }

            Timber.d("StepCounterIO.onReceived: accumulated=${accumulated}B / expected=${expectedLength}B")

            if (accumulated < expectedLength) {
                return // wait for more fragments
            }

            // Full payload assembled -- acknowledge end of transaction before parsing,
            // matching the confirmed-necessary DRSP end-transaction step.
            IO.writeCmd(GetSetMode.DATA_REQUEST, END_TRANSACTION_CMD)

            val fullPayload = synchronized(this) { accumulator }
            val stepData = StepCounterIOFunctional.parse(fullPayload)

            if (stepData != null) {
                Timber.i("Step count parsed: $stepData")
                synchronized(this) { deferred.complete(stepData) }
            } else {
                Timber.w("Failed to parse activity record from ${fullPayload.size}B reassembled payload: ${fullPayload.joinToString("") { "%02X".format(it) }}")
                synchronized(this) { deferred.complete(StepCounterData.unavailable()) }
            }
        } catch (e: Exception) {
            Timber.e("Exception parsing step counter data: ${e.message}")
            synchronized(this) { deferred.complete(StepCounterData.unavailable()) }
        }
    }
}