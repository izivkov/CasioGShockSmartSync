package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
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
    /**
     * Pure parser: Extracts the daily step total from an activity-record payload.
     *
     * Protocol (confirmed from HCI snoop log):
     *   Payload structure of the 0x0014 (CASIO_CONVOY) notification:
     *     [0]      0x26  record type (life-log/activity-record)
     *     [1]      day of week (1=Mon … 7=Sun)
     *     [2]      month
     *     [3]      0x18 = 24 hourly slot count
     *     [4:6]    flags
     *     [6..]    hourly slots: 0xFEFF = empty, or actual per-hour count
     *     [tail]   4-byte sub-record header (skip entirely)
     *     [tail+4] uint32 LE daily step total
     *
     * @param payload The raw bytes from the activity-record notification
     * @return The daily step total, or null if parsing fails
     */
    fun parseStepCount(payload: ByteArray): Int? {
        // Minimum payload size check and record type validation
        if (payload.size < 10 || payload[0].toInt() != 0x26) {
            return null
        }

        // Locate the last 4-byte sentinel (0xFEFFFFFF) and skip the
        // sub-record header (4 bytes) that immediately follows.
        // The step uint32 LE is at the next position.
        val sentinel4 = byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

        // Find all occurrences of the sentinel
        val foundIndices = mutableListOf<Int>()
        for (i in 6 until payload.size - 3) {
            var match = true
            for (j in sentinel4.indices) {
                if (payload[i + j] != sentinel4[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                foundIndices.add(i)
            }
        }

        // Use the last sentinel if found
        if (foundIndices.isNotEmpty()) {
            val lastIndex = foundIndices.last()
            val tailIndex = lastIndex + 4  // byte after last sentinel
            val stepOffset = tailIndex + 4  // skip 4-byte sub-record header
            if (stepOffset + 4 <= payload.size) {
                return bytesToUIntLE(payload, stepOffset)
            }
        }

        // Fallback: scan past 2-byte 0xfeff pairs and zero padding,
        // skip sub-record header, then read the uint32.
        var cursor = 6
        while (cursor + 2 <= payload.size &&
            payload[cursor] == 0xFE.toByte() &&
            payload[cursor + 1] == 0xFF.toByte()) {
            cursor += 2
        }
        while (cursor + 2 <= payload.size &&
            payload[cursor] == 0x00.toByte() &&
            payload[cursor + 1] == 0x00.toByte()) {
            cursor += 2
        }
        cursor += 4  // skip sub-record header
        if (cursor + 4 <= payload.size) {
            return bytesToUIntLE(payload, cursor)
        }

        return null
    }

    /** Pure: Convert 4 bytes at offset to unsigned int (little-endian). */
    private fun bytesToUIntLE(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
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
        val deferredResult: CompletableDeferred<Int>? = null
    )

    private var state = State()

    /**
     * Request step count from the watch.
     * Checks if the current watch model supports step counting before requesting.
     *
     * @return The daily step count, or 0 if the feature is not supported or fails.
     */
    suspend fun request(): Int {
        // Check if watch supports step counter
        if (!WatchInfo.hasStepCounter) {
            Timber.i("Step counter not supported on watch model: ${WatchInfo.model}")
            return 0
        }

        return getStepCount()
    }

    private suspend fun getStepCount(): Int {
        state = state.copy(deferredResult = CompletableDeferred())
        // Send the step counter request: [00 11 00 00 00] to handle 0x0011
        IO.request("11")
        return state.deferredResult?.await() ?: 0
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
            // Skip the first element (characteristic code) and convert to bytes
            val bytes = Utils.byteArrayOfIntArray(intArr.drop(1).toIntArray())

            // Use pure function to parse step count
            val stepCount = StepCounterIOFunctional.parseStepCount(bytes)

            if (stepCount != null) {
                Timber.i("Step count parsed: $stepCount")
                state.deferredResult?.complete(stepCount)
                state = State()
            } else {
                Timber.w("Failed to parse step count from payload")
                state.deferredResult?.complete(0)
                state = State()
            }
        } catch (e: Exception) {
            Timber.e("Exception parsing step counter data: ${e.message}")
            state.deferredResult?.complete(0)
            state = State()
        }
    }
}
