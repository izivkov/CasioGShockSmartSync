package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.Alarms
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

// ============================================================================
// Pure Functional Core: Command Pattern & Data Structures
// ============================================================================

/**
 * Sealed hierarchy for BLE operations as data structures.
 * These represent the 'intent' of operations without executing them.
 */
sealed class BLEAction {
    data class Write(
        val mode: GetSetMode,
        val data: ByteArray
    ) : BLEAction() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Write) return false
            return mode == other.mode && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return 31 * mode.hashCode() + data.contentHashCode()
        }
    }
}

/**
 * Pure functional core for alarm processing logic.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Input -> Deterministic Output transformation.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AlarmsIOFunctional {

    /**
     * Pure parser: Decodes hex string to alarm list.
     * 
     * No side effects - returns parsed alarms for the IO shell to handle.
     */
    fun parseReceivedAlarms(data: String): List<Alarm> = runCatching {
        val decoded = AlarmDecoder.toJson(data).get("ALARMS")
        val gson = Gson()
        val alarmArr = gson.fromJson(decoded.toString(), Array<Alarm>::class.java)
        alarmArr.toList()
    }.getOrElse { error ->
        Timber.e("Failed to parse received alarms: ${error.message}")
        emptyList()
    }

    /**
     * Pure command builder: Creates the sequence of commands to fetch alarms.
     * 
     * Returns data structures (commands) instead of executing them.
     * 
     * IMPORTANT: Commands MUST be executed sequentially. The watch requires a response 
     * to the first command before the second command is sent. Do not parallelize.
     * Order: CASIO_SETTING_FOR_ALM -> (wait for response) -> CASIO_SETTING_FOR_ALM2
     */
    fun buildFetchCommands(): List<BLEAction> = listOf(
        // Get alarm 1 (must complete before ALM2)
        BLEAction.Write(
            GetSetMode.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code.toByte())
        ),
        // Get the rest of the alarms (only after ALM1 response received)
        BLEAction.Write(
            GetSetMode.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code.toByte())
        )
    )

    /**
     * Pure command builder: Creates the sequence of commands to set alarms.
     * 
     * Parses JSON and builds write commands without side effects.
     */
    fun buildSetCommands(message: String): Result<List<BLEAction>> = runCatching {
        JSONObject(message).get("value").let { it as JSONArray }.let { jsonArray ->
            val firstAlarm = Alarms.fromJsonAlarmFirstAlarm(jsonArray.getJSONObject(0))
            val secondaryAlarms = Alarms.fromJsonAlarmSecondaryAlarms(jsonArray)
            listOf(
                BLEAction.Write(GetSetMode.SET, firstAlarm),
                BLEAction.Write(GetSetMode.SET, secondaryAlarms)
            )
        }
    }

    /**
     * Pure validation: Checks if all alarms have been received.
     */
    fun isAlarmCountComplete(alarmCount: Int, expectedCount: Int = 5): Boolean =
        alarmCount == expectedCount
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

@RequiresApi(Build.VERSION_CODES.O)
object AlarmsIO {
    data class AlarmIOState(
        val deferredResult: CompletableDeferred<ArrayList<Alarm>>? = null,
        val alarms: ArrayList<Alarm> = ArrayList(),
        val isProcessing: Boolean = false,
        val error: String? = null
    )

    private var state = AlarmIOState()

    private fun updateState(transform: (AlarmIOState) -> AlarmIOState): AlarmIOState =
        synchronized(this) {
            state = transform(state)
            state
        }

    suspend fun request(): ArrayList<Alarm> = CachedIO.request("GET_ALARMS") { key ->
        updateState {
            it.copy(
                deferredResult = CompletableDeferred(), isProcessing = true
            )
        }
        Alarm.clear()
        Connection.sendMessage("{ action: '$key'}")
        state.deferredResult?.await() ?: ArrayList()
    }

    fun set(alarms: ArrayList<Alarm>) {
        if (alarms.isEmpty()) {
            updateState { it.copy(error = "Alarm model not initialised!") }
            return
        }

        CachedIO.set("GET_ALARMS") {
            val json = Gson().toJson(alarms)
            Connection.sendMessage("{action: \"SET_ALARMS\", value: $json }")
        }
    }

    fun onReceived(data: String) {
        // Use pure function to parse
        val parsedAlarms = AlarmsIOFunctional.parseReceivedAlarms(data)
        Alarm.addSorted(parsedAlarms.toTypedArray())

        // Use pure function to check completion
        // State accumulation: response 1 contains 1 alarm, response 2 contains 4 alarms = 5 total
        // This mechanism respects the sequential command requirement automatically
        if (AlarmsIOFunctional.isAlarmCountComplete(Alarm.getAlarms().size)) {
            updateState { currentState ->
                currentState.copy(
                    alarms = Alarm.getAlarms(), isProcessing = false
                )
            }
            state.deferredResult?.complete(state.alarms)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        // Use pure function to build commands, then execute them sequentially
        // The forEach ensures sequential dispatch to the watch (required by device protocol)
        AlarmsIOFunctional.buildFetchCommands().forEach { action ->
            when (action) {
                is BLEAction.Write -> IO.writeCmd(action.mode, action.data)
            }
        }
    }

    fun sendToWatchSet(message: String) {
        // Use pure function to build commands, then execute them
        AlarmsIOFunctional.buildSetCommands(message)
            .onSuccess { commands ->
                commands.forEach { action ->
                    when (action) {
                        is BLEAction.Write -> IO.writeCmd(action.mode, action.data)
                    }
                }
            }
            .onFailure { error ->
                Timber.e("Failed to set alarms: ${error.message}")
            }
    }
}

// ============================================================================
// Pure Decoder: Alarm Protocol Decoding
// ============================================================================

/**
 * Pure decoder for alarm protocol data.
 * No mutable state or side effects - pure transformations only.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AlarmDecoder {
    private const val HOURLY_CHIME_MASK = 0b10000000

    /**
     * Converts a command string to a JSON object containing alarm data.
     *
     * Command buffer structure:
     * [0] - Command type:
     *     - 0x2A: Single alarm (CASIO_SETTING_FOR_ALM)
     *     - 0x2B: Multiple alarms (CASIO_SETTING_FOR_ALM2)
     *
     * For single alarm (0x2A):
     * [1..4] - One 4-byte alarm entry
     *
     * For multiple alarms (0x2B):
     * [1..16] - Four 4-byte alarm entries
     *
     * Each alarm entry is 4 bytes:
     * [0] - Flags byte:
     *     - bit 7 (0x80): Hourly chime enabled
     *     - bit 0 (0x01): Alarm enabled
     * [1] - Constant value (0x40)
     * [2] - Hour (0-23)
     * [3] - Minute (0-59)
     *
     * @param command The raw command string containing alarm data
     * @return JSONObject with format: {"ALARMS": [array of alarm objects]}
     */
    fun toJson(command: String): JSONObject = runCatching {
        Utils.toIntArray(command).let { intArray ->
            JSONArray().apply {
                when (intArray.firstOrNull()) {
                    CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code -> ArrayList(
                        intArray.drop(1)
                    ).let(::createJsonAlarm).let(::put)

                    CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code -> intArray.drop(
                        1
                    ).chunked(4).map { ArrayList(it) }.forEach { put(createJsonAlarm(it)) }

                    else -> Timber.d("Unhandled Command [$command]")
                }
            }
        }.let { alarms -> JSONObject().put("ALARMS", alarms) }
    }.getOrElse { error ->
        Timber.e("Failed to parse command: ${error.message}")
        JSONObject()
    }

    /**
     * Pure parser: Creates a JSON object from a 4-byte alarm data buffer.
     * 
     * Buffer structure:
     * [0] - Flags byte:
     *     - bit 7 (0x80): Hourly chime enabled
     *     - bit 0 (0x01): Alarm enabled
     * [1] - Constant value (typically 0x40)
     * [2] - Hour (0-23)
     * [3] - Minute (0-59)
     *
     * @param intArray ArrayList containing 4 bytes of alarm data
     * @return JSONObject containing the parsed alarm data
     */
    private fun createJsonAlarm(intArray: ArrayList<Int>): JSONObject = runCatching {
        Alarms.Alarm(
            hour = intArray[2],
            minute = intArray[3],
            enabled = intArray[0] and Alarms.ENABLED_MASK != 0,
            hasHourlyChime = intArray[0] and HOURLY_CHIME_MASK != 0
        ).let { alarm ->
            JSONObject(Gson().toJson(alarm))
        }
    }.getOrElse { error ->
        Timber.e("Failed to create alarm: ${error.message}")
        JSONObject()
    }
}
