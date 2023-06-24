package org.avmedia.gshockapi.io

import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.Alarms
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object AlarmsIO {

    suspend fun request(): ArrayList<Alarm> {
        return CachedIO.request("GET_ALARMS", ::getAlarms) as ArrayList<Alarm>
    }

    private suspend fun getAlarms(key: String): ArrayList<Alarm> {
        Connection.sendMessage("{ action: '$key'}")

        Alarm.clear()

        var deferredResult = CompletableDeferred<ArrayList<Alarm>>()
        CachedIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        CachedIO.subscribe("ALARMS") { keyedData ->
            val data = keyedData.getString("value")
            val key = "GET_ALARMS"

            fun fromJson(jsonStr: String) {
                val gson = Gson()
                val alarmArr = gson.fromJson(jsonStr, Array<Alarm>::class.java)
                Alarm.alarms.addAll(alarmArr)
            }

            fromJson(data)

            if (Alarm.alarms.size > 1) {
                CachedIO.resultQueue.dequeue(key)?.complete(Alarm.alarms)
            }
        }
        return deferredResult.await()
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

        // remove from cache
        CachedIO.cache.remove("GET_ALARMS")

        Connection.sendMessage("{action: \"SET_ALARMS\", value: ${toJson()} }")
    }

    fun toJson(data: String): JSONObject {
        return JSONObject().put(
            "ALARMS",
            JSONObject().put("value", AlarmDecoder.toJson(data).get("ALARMS"))
                .put("key", "GET_ALARMS")
        )
    }

    // watch senders
    fun sendToWatch(message: String) {
        // get alarm 1
        CasioIO.writeCmd(
            0x000c,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code.toByte())
        )

        // get the rest of the alarms
        CasioIO.writeCmd(
            0x000c,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        val alarmsJsonArr: JSONArray = JSONObject(message).get("value") as JSONArray
        val alarmCasio0 = Alarms.fromJsonAlarmFirstAlarm(alarmsJsonArr[0] as JSONObject)
        CasioIO.writeCmd(0x000e, alarmCasio0)
        var alarmCasio: ByteArray = Alarms.fromJsonAlarmSecondaryAlarms(alarmsJsonArr)
        CasioIO.writeCmd(0x000e, alarmCasio)
    }

    object AlarmDecoder {
        private const val HOURLY_CHIME_MASK = 0b10000000

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