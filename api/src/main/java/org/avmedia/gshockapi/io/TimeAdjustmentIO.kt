package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.model.Settings
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber

@androidx.annotation.Keep
class TimeAdjustmentInfo(
    var isTimeAdjustmentSet: Boolean = false,
    var adjustmentTimeMinutes: Int = 0,
)

// ============================================================================
// Pure Functional Core: Time Adjustment Data Processing
// ============================================================================

/**
 * Pure functional core for time adjustment processing.
 *
 * All methods are pure: no mutable state, no side effects.
 * Handles parsing and encoding of time adjustment settings.
 */
@RequiresApi(Build.VERSION_CODES.O)
object TimeAdjustmentIOFunctional {
    /**
     * Pure parser: Checks if time adjustment is enabled.
     *
     * Protocol format:
     * [12] - bit 7: time adjustment flag:
     *     0 = syncing on  (time adjustment enabled), e.g. 0x00
     *     1 = syncing off (time adjustment disabled), e.g. 0x80
     */
    fun parseIsTimeAdjustmentSet(data: String): Boolean = Utils.toIntArray(data)[12] and SYNC_OFF == 0

    /**
     * Pure parser: Extracts the time adjustment minutes.
     *
     * Protocol format:
     * [13] - Adjustment time in minutes (0-59)
     * Returns 30 if value is out of valid range.
     */
    fun parseAdjustmentTimeMinutes(data: String): Int =
        Utils.toIntArray(data)[13].let { minutesRead ->
            if (minutesRead in 0..59) minutesRead else 30
        }

    /**
     * Pure parser: Parses complete time adjustment info.
     *
     * No side effects - pure extraction.
     */
    fun decode(data: String): Result<TimeAdjustmentInfo> =
        runCatching {
            TimeAdjustmentInfo(
                isTimeAdjustmentSet = parseIsTimeAdjustmentSet(data),
                adjustmentTimeMinutes = parseAdjustmentTimeMinutes(data),
            )
        }

    /**
     * Pure encoder: Encodes time adjustment settings to byte array.
     *
     * Takes original data and applies new settings without side effects.
     * Protocol format:
     * [12] - bit 7: time adjustment flag (0 = on, 1 = off). Some watches keep other
     *        data in bits 0-6 (e.g. the hour of the first automatic sync), so only
     *        bit 7 is changed.
     * [13] - Adjustment time minutes
     */
    fun encode(
        originalData: String,
        settings: JSONObject,
    ): Result<ByteArray> =
        runCatching {
            encode(
                originalData,
                timeAdjustment = settings.get("timeAdjustment") == true,
                adjustmentTimeMinutes = settings.getInt("adjustmentTimeMinutes"),
            ).getOrThrow()
        }

    fun encode(
        originalData: String,
        timeAdjustment: Boolean,
        adjustmentTimeMinutes: Int,
    ): Result<ByteArray> =
        runCatching {
            if (originalData.isEmpty()) {
                throw IllegalArgumentException("Original data is empty")
            }

            val intArr =
                Utils.toIntArray(originalData).apply {
                    // syncing off: 110f0f0f0600500004000100->80<-37d2
                    // syncing on:  110f0f0f0600500004000100->00<-37d2
                    this[12] = (this[12] and SYNC_OFF.inv()) or (if (timeAdjustment) 0 else SYNC_OFF)
                    this[13] = adjustmentTimeMinutes
                }
            val size = intArr.size
            intArr.foldIndexed(ByteArray(size)) { i, array, value ->
                array.apply { set(i, value.toByte()) }
            }
        }

    private const val SYNC_OFF = 0x80

    /**
     * Pure command builder: Creates fetch command for time adjustment.
     */
    fun buildFetchCommand(): ByteArray = Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code.toByte())
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Time Adjustment IO handler with state management.
 *
 * Manages time adjustment/synchronization settings.
 * Stores original data to preserve unchanged fields when encoding.
 * Uses pure functional core for all parsing and encoding operations.
 */
@RequiresApi(Build.VERSION_CODES.O)
object TimeAdjustmentIO {
    private data class State(
        val deferredResult: CompletableDeferred<TimeAdjustmentInfo>? = null,
    )

    private var state = State()

    /**
     * Stores the original time adjustment data from the watch.
     * Used to preserve unchanged fields when encoding modifications.
     */
    object CasioIsAutoTimeOriginalValue {
        var value = ""
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(): TimeAdjustmentInfo {
        if (!WatchInfo.hasTimeAdjustment) {
            return TimeAdjustmentInfo()
        }
        return CachedIO.request("GET_TIME_ADJUSTMENT") { key -> getTimeAdjustment(key) }
    }

    private suspend fun getTimeAdjustment(key: String): TimeAdjustmentInfo {
        val deferred = CompletableDeferred<TimeAdjustmentInfo>()
        synchronized(this) {
            state = state.copy(deferredResult = deferred)
        }
        Connection.sendMessage("{ action: '$key'}")
        return deferred.await()
    }

    fun set(settings: Settings) {
        val settingJson = Gson().toJson(settings)
        Connection.sendMessage("{action: \"SET_TIME_ADJUSTMENT\", value: $settingJson}")
        // Keep the new value instead of reading it back: a read sent now can reach the
        // watch before the queued write, and the watch then answers with the old value.
        CachedIO.put(
            "GET_TIME_ADJUSTMENT",
            TimeAdjustmentInfo(settings.timeAdjustment, settings.adjustmentTimeMinutes),
        )
    }

    fun onReceived(data: String) {
        // Store original data for later encoding
        CasioIsAutoTimeOriginalValue.value = data

        // Use pure function to decode
        TimeAdjustmentIOFunctional.decode(data)
            .fold(
                onSuccess = { info ->
                    synchronized(this) {
                        state.deferredResult?.complete(info)
                        state = state.copy(deferredResult = null)
                    }
                },
                onFailure = { error ->
                    Timber.e("Failed to decode time adjustment: ${error.message}")
                    synchronized(this) {
                        state.deferredResult?.completeExceptionally(error)
                        state = state.copy(deferredResult = null)
                    }
                },
            )
    }

    fun onRunError() {
        synchronized(this) {
            state.deferredResult?.complete(TimeAdjustmentInfo())
            state = state.copy(deferredResult = null)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        // Use pure function to build command
        IO.writeCmd(
            GetSetMode.GET,
            TimeAdjustmentIOFunctional.buildFetchCommand(),
        )
    }

    fun sendToWatchSet(message: String) {
        // Use pure function to encode settings
        JSONObject(message).get("value").let { it as JSONObject }
            .let { settings ->
                TimeAdjustmentIOFunctional.encode(CasioIsAutoTimeOriginalValue.value, settings)
            }
            .fold(
                onSuccess = { encodedData ->
                    IO.writeCmd(GetSetMode.SET, encodedData)
                    // The next change in this connection starts from what was written.
                    CasioIsAutoTimeOriginalValue.value = Utils.fromByteArrayToHexStr(encodedData)
                },
                onFailure = { error ->
                    Timber.e("Failed to encode time adjustment: ${error.message}")
                },
            )
    }
}
