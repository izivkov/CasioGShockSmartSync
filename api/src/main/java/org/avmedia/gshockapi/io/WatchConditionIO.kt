package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber
import kotlin.math.roundToInt

// ============================================================================
// Pure Functional Core: Watch Condition Decoding
// ============================================================================

/**
 * Pure functional core for watch condition processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles battery level and temperature extraction with calibration.
 */
@RequiresApi(Build.VERSION_CODES.O)
object WatchConditionIOFunctional {
    data class WatchConditionValue(val batteryLevel: Int, val temperature: Int)

    /**
     * Pure decoder: Extracts battery level and temperature from raw data.
     * 
     * Protocol format:
     * [1] - Battery raw level (calibrated using watch-specific limits)
     * [2] - Temperature in Celsius
     * 
     * Battery calibration: Maps raw value to 0-100% using watch-specific
     * lower and upper limits from WatchInfo.batteryLevelLowerLimit/UpperLimit
     * 
     * No side effects - pure extraction.
     */
    fun decode(data: String): Result<WatchConditionValue> = runCatching {
        val intArr = Utils.toIntArray(data)
        val bytes = Utils.byteArrayOfIntArray(intArr.drop(1).toIntArray())

        if (bytes.size < 2) {
            throw IllegalArgumentException("Watch condition data too short: ${bytes.size} bytes (need 2)")
        }

        val batteryRawValue = bytes[0].toInt()
        Timber.i("battery level raw value: $batteryRawValue")

        // Calibrate battery level using watch-specific limits
        val batteryLevelLowerLimit = WatchInfo.batteryLevelLowerLimit
        val batteryLevelUpperLimit = WatchInfo.batteryLevelUpperLimit
        val range = batteryLevelUpperLimit - batteryLevelLowerLimit
        
        if (range <= 0) {
            throw IllegalArgumentException("Invalid battery limit range: $range")
        }

        val multiplier: Int = (100.0 / range).roundToInt()
        val batteryLevel = (batteryRawValue - batteryLevelLowerLimit)
        val batteryLevelPercent: Int = (batteryLevel * multiplier).coerceIn(0, 100)
        val temperature: Int = bytes[1].toInt()

        WatchConditionValue(batteryLevelPercent, temperature)
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Watch Condition IO handler with state management.
 * 
 * Provides access to watch battery level and temperature information.
 * Uses pure functional core for data decoding and calibration.
 */
@RequiresApi(Build.VERSION_CODES.O)
object WatchConditionIO {
    class WatchConditionValue(val batteryLevel: Int, val temperature: Int)

    private data class State(
        val deferredResult: CompletableDeferred<WatchConditionValue>? = null
    )

    private var state = State()

    suspend fun request(): WatchConditionValue =
        CachedIO.request("28") { key -> getWatchCondition(key) }

    private suspend fun getWatchCondition(key: String): WatchConditionValue {
        state = state.copy(deferredResult = CompletableDeferred())
        IO.request(key)
        return state.deferredResult?.await() ?: WatchConditionValue(0, 0)
    }

    fun onReceived(data: String) {
        // Use pure function to decode watch condition
        WatchConditionIOFunctional.decode(data)
            .fold(
                onSuccess = { condition ->
                    // Convert to our public data class
                    state.deferredResult?.complete(
                        WatchConditionValue(condition.batteryLevel, condition.temperature)
                    )
                    state = State()
                },
                onFailure = { error ->
                    Timber.e("Failed to decode watch condition: ${error.message}")
                    state.deferredResult?.complete(WatchConditionValue(0, 0))
                    state = State()
                }
            )
    }
}
