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


// ============================================================================
// Pure Functional Core: Step Counter Decoding
// ============================================================================

/**
 * Pure functional core for step counter processing.
 *
 * All methods are pure: no mutable state, no side effects.
 * Handles step count extraction from activity-record (life-log) payloads.
 */
@RequiresApi(Build.VERSION_CODES.O)
object StepCounterIOFunctional {
    private const val HEADER_SIZE = 6
    private const val HOURLY_SLOT_COUNT = 144
    private const val HOURLY_SLOT_SIZE = 2
    private const val BETWEEN_HISTORY_PADDING_SIZE = 24
    private const val DAILY_SLOT_COUNT = 14
    private const val DAILY_SLOT_SIZE = 4

    /**
     * Decodes the fixed 400-byte activity record returned by an ABL-100WE.
     * The offsets are verified against the supplied official-app HCI capture:
     * the current-day field at byte 374 is `71 1A 00 00` (6,769 steps).
     */
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
        return value.takeUnless { it == 0xFFFE }
    }

    private fun ByteArray.readUnsignedIntOrNull(offset: Int): Int? {
        if (offset + 4 > size) return null
        val value = (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
        return value.takeUnless { it == -2 }
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Step Counter IO handler with state management.
 *
 * Provides access to daily step count from compatible watches.
 * Uses pure functional core for data decoding.
 */
@RequiresApi(Build.VERSION_CODES.O)
object StepCounterIO {
    private data class State(
        val deferredResult: CompletableDeferred<StepCounterData>? = null
    )

    private var state = State()

    /**
     * Request step count from the watch.
     * Checks if the current watch model supports step counting before requesting.
     *
     * @return The ABL-100WE life-log record, or an unavailable record if unsupported or timed out.
     */
    suspend fun request(): StepCounterData {
        // Check if watch supports step counter
        if (!WatchInfo.hasStepCounter) {
            Timber.i("Step counter not supported on watch model: ${WatchInfo.model}")
            return StepCounterData.unavailable()
        }

        return getStepCount()
    }

    private suspend fun getStepCount(): StepCounterData {
        val deferred = CompletableDeferred<StepCounterData>()
        synchronized(this) {
            state = state.copy(deferredResult = deferred)
        }
        // The activity-record request is sent to 26eb0023, not the regular
        // register-request characteristic. The response arrives on 26eb0024.
        IO.writeCmd(GetSetMode.DATA_REQUEST, byteArrayOf(0x00, 0x11, 0x00, 0x00, 0x00))
        return withTimeoutOrNull(10_000) { deferred.await() }
            ?: StepCounterData.unavailable().also {
                synchronized(this) {
                    if (state.deferredResult === deferred) state = state.copy(deferredResult = null)
                }
            }
    }

    /**
     * Called when activity-record notification data is received.
     * Parses the payload to extract and store the step count.
     *
     * @param data The notification payload as a string of space-separated hex values
     */
    fun onReceived(data: String) {
        try {
            // Convert string data to byte array
            val intArr = Utils.toIntArray(data)
            // The dispatcher supplies the complete 0x26 activity-record payload.
            val bytes = Utils.byteArrayOfIntArray(intArr.toIntArray())

            // Use pure function to parse step count
            val stepCount = StepCounterIOFunctional.parse(bytes)

            if (stepCount != null) {
                Timber.i("Step count parsed: $stepCount")
                synchronized(this) {
                    state.deferredResult?.complete(stepCount)
                    state = state.copy(deferredResult = null)
                }
            } else {
                Timber.w("Failed to parse step count from payload")
                synchronized(this) {
                    state.deferredResult?.complete(StepCounterData.unavailable())
                    state = state.copy(deferredResult = null)
                }
            }
        } catch (e: Exception) {
            Timber.e("Exception parsing step counter data: ${e.message}")
            synchronized(this) {
                state.deferredResult?.complete(StepCounterData.unavailable())
                state = state.copy(deferredResult = null)
            }
        }
    }
}
