package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GET_SET_MODE
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

class TimeAdjustmentInfo(
    var isTimeAdjustmentSet: Boolean = false,
    var adjustmentTimeMinutes: Int = 0
)

@RequiresApi(Build.VERSION_CODES.O)
object TimeAdjustmentIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<TimeAdjustmentInfo>
    }

    suspend fun request(): TimeAdjustmentInfo {
        return CachedIO.request("GET_TIME_ADJUSTMENT", ::getTimeAdjustment) as TimeAdjustmentInfo
    }

    private suspend fun getTimeAdjustment(key: String): TimeAdjustmentInfo {
        DeferredValueHolder.deferredResult = CompletableDeferred()
        Connection.sendMessage("{ action: '$key'}")
        return DeferredValueHolder.deferredResult.await()
    }

    fun set(settings: Settings) {
        val settingJson = Gson().toJson(settings)

        fun setFunc () {Connection.sendMessage("{action: \"SET_TIME_ADJUSTMENT\", value: ${settingJson}}")}
        CachedIO.set("GET_TIME_ADJUSTMENT", ::setFunc)
    }

    fun onReceived(data: String) {
        val timeAdjustmentSet = isTimeAdjustmentSet(data)
        val adjustmentTimeMinutes = timeOfAdjustmentMinutes(data)
        CasioIsAutoTimeOriginalValue.value = data
        DeferredValueHolder.deferredResult.complete(
            TimeAdjustmentInfo(
                timeAdjustmentSet,
                adjustmentTimeMinutes
            )
        )
    }

    private fun isTimeAdjustmentSet(data: String): Boolean {
        // syncing off: 110f0f0f0600500004000100->80<-37d2
        // syncing on:  110f0f0f0600500004000100->00<-37d2

        CasioIsAutoTimeOriginalValue.value = data // save original data for future use
        return Utils.toIntArray(data)[12] == 0
    }

    private fun timeOfAdjustmentMinutes(data: String): Int {
        // syncing off: 110f0f0f060050000400010080->37<-d2
        val minutesRead = Utils.toIntArray(data)[13]

        val range = 0..59
        val minutes = if (minutesRead in range) {
            minutesRead
        } else {
            30
        }

        return minutes
    }

    object CasioIsAutoTimeOriginalValue {
        var value = ""
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        IO.writeCmd(
            GET_SET_MODE.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        val settings = JSONObject(message).get("value") as JSONObject
        // add the original string from Casio, so we do not mess up any ot the other settings.
        settings.put(
            "casioIsAutoTimeOriginalValue",
            CasioIsAutoTimeOriginalValue.value
        )
        val encodedTimeAdj = encodeTimeAdjustment(settings)
        if (encodedTimeAdj.isNotEmpty()) {
            IO.writeCmd(GET_SET_MODE.SET, encodedTimeAdj)
        }
    }

    private fun encodeTimeAdjustment(settings: JSONObject): ByteArray {

        val casioIsAutoTimeOriginalValue = settings.getString("casioIsAutoTimeOriginalValue")
        if (casioIsAutoTimeOriginalValue.isEmpty()) {
            return "".toByteArray()
        }

        // syncing off: 110f0f0f0600500004000100->80<-37d2
        // syncing on:  110f0f0f0600500004000100->00<-37d2

        val intArray = Utils.toIntArray(casioIsAutoTimeOriginalValue)

        if (settings.get("timeAdjustment") == true) {
            intArray[12] = 0x00
        } else {
            intArray[12] = 0x80
        }

        val adjustmentTimeMinutes = settings.getInt("adjustmentTimeMinutes")
        intArray[13] = adjustmentTimeMinutes

        return intArray.foldIndexed(ByteArray(intArray.size)) { i, a, v ->
            a.apply {
                set(
                    i,
                    v.toByte()
                )
            }
        }
    }
}