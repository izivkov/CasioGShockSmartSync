package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

class TimeAdjustmentInfo(
    var isTimeAdjustmentSet: Boolean = false,
    var adjustmentTimeMinutes: Int = 0
)

@RequiresApi(Build.VERSION_CODES.O)
object TimeAdjustmentIO {
    private data class State(
        val deferredResult: CompletableDeferred<TimeAdjustmentInfo>? = null
    )

    private var state = State()

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(): TimeAdjustmentInfo =
        CachedIO.request("GET_TIME_ADJUSTMENT") { key -> getTimeAdjustment(key) }

    private suspend fun getTimeAdjustment(key: String): TimeAdjustmentInfo {
        state = state.copy(deferredResult = CompletableDeferred())
        Connection.sendMessage("{ action: '$key'}")
        return state.deferredResult?.await() ?: error("Deferred result not initialized")
    }

    fun set(settings: Settings) {
        settings.let {
            Gson().toJson(it)
        }.let { settingJson ->
            CachedIO.set("GET_TIME_ADJUSTMENT") {
                Connection.sendMessage("{action: \"SET_TIME_ADJUSTMENT\", value: $settingJson}")
            }
        }
    }

    fun onReceived(data: String) {
        data.let { input ->
            CasioIsAutoTimeOriginalValue.value = input
            TimeAdjustmentInfo(
                isTimeAdjustmentSet = isTimeAdjustmentSet(input),
                adjustmentTimeMinutes = timeOfAdjustmentMinutes(input)
            )
        }.let { info ->
            state.deferredResult?.complete(info)
            state = State() // Reset state
        }
    }

    private fun isTimeAdjustmentSet(data: String): Boolean =
        Utils.toIntArray(data)[12] == 0

    private fun timeOfAdjustmentMinutes(data: String): Int =
        Utils.toIntArray(data)[13].let { minutesRead ->
            if (minutesRead in 0..59) minutesRead else 30
        }

    object CasioIsAutoTimeOriginalValue {
        var value = ""
    }

    @Suppress("UNUSED_PARAMETER")
    fun sendToWatch(message: String) {
        IO.writeCmd(
            GetSetMode.GET,
            Utils.byteArray(CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code.toByte())
        )
    }

    fun sendToWatchSet(message: String) {
        JSONObject(message).get("value").let { it as JSONObject }
            .apply {
                put("casioIsAutoTimeOriginalValue", CasioIsAutoTimeOriginalValue.value)
            }
            .let { settings -> encodeTimeAdjustment(settings) }
            .takeIf { it.isNotEmpty() }
            ?.let { encoded -> IO.writeCmd(GetSetMode.SET, encoded) }
    }

    private fun encodeTimeAdjustment(settings: JSONObject): ByteArray =
        settings.getString("casioIsAutoTimeOriginalValue")
            .takeIf { it.isNotEmpty() }
            ?.let { value ->
                Utils.toIntArray(value)
                    .apply {
                        // syncing off: 110f0f0f0600500004000100->80<-37d2
                        // syncing on:  110f0f0f0600500004000100->00<-37d2
                        this[12] = if (settings.get("timeAdjustment") == true) 0x00 else 0x80
                        this[13] = settings.getInt("adjustmentTimeMinutes")
                    }
                    .let { intArray ->
                        intArray.foldIndexed(ByteArray(intArray.size)) { i, array, value ->
                            array.apply { set(i, value.toByte()) }
                        }
                    }
            } ?: ByteArray(0)
}
