package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GET_SET_MODE
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

    @Suppress("UNCHECKED_CAST")
    suspend fun request(): ArrayList<Alarm> {
        return CachedIO.request("GET_ALARMS", ::getAlarms) as ArrayList<Alarm>
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

        fun setFunc () {Connection.sendMessage("{action: \"SET_ALARMS\", value: ${toJson()} }")}
        CachedIO.set("SET_ALARMS", ::setFunc)
    }

    fun onReceived(data: String) {

        fun fromJson(jsonStr: String) {
            val gson = Gson()
            val alarmArr = gson.fromJson(jsonStr, Array<Alarm>::class.java)
            Alarm.addSorted(alarmArr)
        }

        val decoded = AlarmDecoder.toJson(data).get("ALARMS")
        fromJson(decoded.toString())

        if (Alarm.alarms.size == 5) {
            DeferredValueHolder.deferredResult.complete(Alarm.alarms)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        // get alarm 1
        IO.writeCmd(
            GET_SET_MODE.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code.toByte())
        )

        // get the rest of the alarms
        IO.writeCmd(
            GET_SET_MODE.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        val alarmsJsonArr: JSONArray = JSONObject(message).get("value") as JSONArray
        val alarmCasio0 = Alarms.fromJsonAlarmFirstAlarm(alarmsJsonArr[0] as JSONObject)
        IO.writeCmd(GET_SET_MODE.SET, alarmCasio0)
        val alarmCasio: ByteArray = Alarms.fromJsonAlarmSecondaryAlarms(alarmsJsonArr)
        IO.writeCmd(GET_SET_MODE.SET, alarmCasio)
    }

    object AlarmDecoder {
        private const val HOURLY_CHIME_MASK = 0b10000000
        private val alarmsQueue = mutableListOf<ArrayList<Int>>()

        fun toJsonNew(command: String) {
            alarmsQueue.add(Utils.toIntArray(command))
        }

        fun toJson(command: String): JSONObject {
            val jsonResponse = JSONObject()
            val intArray = Utils.toIntArray(command)
            val alarms = JSONArray()

            when (intArray[0]) {
                CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code -> {
                    intArray.removeAt(0)
                    alarms.put(createJsonAlarm(intArray))
                    jsonResponse.put("ALARMS", alarms)
                }

                CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code -> {
                    intArray.removeAt(0)
                    val multipleAlarms = intArray.chunked(4)
                    multipleAlarms.forEach {
                        alarms.put(createJsonAlarm(it as ArrayList<Int>))
                    }
                    jsonResponse.put("ALARMS", alarms)
                }

                else -> {
                    Timber.d("Unhandled Command [$command]")
                }
            }

            return jsonResponse
        }

        private fun createJsonAlarm(intArray: ArrayList<Int>): JSONObject {
            val alarm = Alarms.Alarm(
                intArray[2],
                intArray[3],
                intArray[0] and Alarms.ENABLED_MASK != 0,
                intArray[0] and HOURLY_CHIME_MASK != 0
            )
            val gson = Gson()
            return JSONObject(gson.toJson(alarm))
        }
    }
}