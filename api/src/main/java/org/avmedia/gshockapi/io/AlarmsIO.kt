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
        val decoded = AlarmDecoder.toJson(data).get("ALARMS")
        val gson = Gson()
        val alarmArr = gson.fromJson(decoded.toString(), Array<Alarm>::class.java)
        Alarm.addSorted(alarmArr)

        if (Alarm.getAlarms().size == 5) { // Must be 5 even if WatchInfo.alarmCount is 4.
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
        // get alarm 1
        IO.writeCmd(
            GetSetMode.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code.toByte())
        )

        // get the rest of the alarms
        IO.writeCmd(
            GetSetMode.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        runCatching {
            JSONObject(message).get("value").let { it as JSONArray }.let { jsonArray ->
                    val first = Alarms.fromJsonAlarmFirstAlarm(jsonArray.getJSONObject(0))
                    println("First Alarm: $first, in binary: ${Utils.fromByteArrayToHexStr(first)}")
                    Pair(
                        Alarms.fromJsonAlarmFirstAlarm(jsonArray.getJSONObject(0)),
                        Alarms.fromJsonAlarmSecondaryAlarms(jsonArray)
                    )
                }.also { (firstAlarm, secondaryAlarms) ->
                    IO.writeCmd(GetSetMode.SET, firstAlarm)
                    IO.writeCmd(GetSetMode.SET, secondaryAlarms)
                }
        }.onFailure { error ->
            Timber.e("Failed to set alarms: ${error.message}")
        }
    }

    object AlarmDecoder {
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
         * Creates a JSON object from a 4-byte alarm data buffer.
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
        private const val HOURLY_CHIME_MASK = 0b10000000

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
}
