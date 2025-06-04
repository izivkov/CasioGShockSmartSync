package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.WatchInfo
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

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<ArrayList<Alarm>>
    }

    suspend fun request(): ArrayList<Alarm> {
        return CachedIO.request("GET_ALARMS") { key ->
            getAlarms(key)
        }
    }

    private suspend fun getAlarms(key: String): ArrayList<Alarm> {
        DeferredValueHolder.deferredResult = CompletableDeferred()
        Alarm.clear()
        Connection.sendMessage("{ action: '$key'}")

        return DeferredValueHolder.deferredResult.await()
    }

    fun set(alarms: ArrayList<Alarm>) {
        if (alarms.isEmpty()) {
            Timber.d("Alarm model not initialised! Cannot set alarm")
            return
        }

        @Synchronized
        fun toJson(): String {
            val gson = Gson()
            return gson.toJson(alarms)
        }

        fun setFunc() {
            Connection.sendMessage("{action: \"SET_ALARMS\", value: ${toJson()} }")
        }
        CachedIO.set("SET_ALARMS") {
            setFunc()
        }
    }

    fun onReceived(data: String) {

        fun fromJson(jsonStr: String) {
            val gson = Gson()
            val alarmArr = gson.fromJson(jsonStr, Array<Alarm>::class.java)
            Alarm.addSorted(alarmArr)
        }

        val decoded = AlarmDecoder.toJson(data).get("ALARMS")
        fromJson(decoded.toString())

        if (Alarm.getAlarms().size == WatchInfo.alarmCount) {
            DeferredValueHolder.deferredResult.complete(Alarm.getAlarms())
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
            JSONObject(message)
                .get("value")
                .let { it as JSONArray }
                .let { jsonArray ->
                    Pair(
                        Alarms.fromJsonAlarmFirstAlarm(jsonArray.getJSONObject(0)),
                        Alarms.fromJsonAlarmSecondaryAlarms(jsonArray)
                    )
                }
                .also { (firstAlarm, secondaryAlarms) ->
                    IO.writeCmd(GetSetMode.SET, firstAlarm)
                    IO.writeCmd(GetSetMode.SET, secondaryAlarms)
                }
        }.onFailure { error ->
            Timber.e("Failed to set alarms: ${error.message}")
        }
    }

    object AlarmDecoder {
        fun toJson(command: String): JSONObject =
            runCatching {
                Utils.toIntArray(command)
                    .let { intArray ->
                        JSONArray().apply {
                            when (intArray.firstOrNull()) {
                                CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code ->
                                    ArrayList(intArray.drop(1))
                                        .let(::createJsonAlarm)
                                        .let(::put)

                                CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code ->
                                    intArray.drop(1)
                                        .chunked(4)
                                        .map { ArrayList(it) }
                                        .forEach { put(createJsonAlarm(it)) }

                                else -> Timber.d("Unhandled Command [$command]")
                            }
                        }
                    }
                    .let { alarms -> JSONObject().put("ALARMS", alarms) }
            }.getOrElse { error ->
                Timber.e("Failed to parse command: ${error.message}")
                JSONObject()
            }

        private fun createJsonAlarm(intArray: ArrayList<Int>): JSONObject =
            runCatching {
                val HOURLY_CHIME_MASK = 0b10000000

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
