package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.roundToInt

object WatchConditionIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<WatchConditionValue>
    }

    class WatchConditionValue(val batteryLevel: Int, val temperature: Int)

    suspend fun request(): WatchConditionValue {
        return CachedIO.request("28", ::getWatchCondition) as WatchConditionValue
    }

    private suspend fun getWatchCondition(key: String): WatchConditionValue {
        DeferredValueHolder.deferredResult =  CompletableDeferred()
        CasioIO.request(key)
        return DeferredValueHolder.deferredResult.await()
    }

    fun onReceived(data: String) {
        DeferredValueHolder.deferredResult.complete(WatchConditionDecoder.decodeValue(data))
    }

    object WatchConditionDecoder {
        fun decodeValue(data: String): WatchConditionValue {
            if (data == null) {
                return WatchConditionValue(0, 0)
            }
            val intArr = Utils.toIntArray(data)
            val bytes = Utils.byteArrayOfIntArray(intArr.drop(1).toIntArray())

            if (bytes.size >= 2) {
                // Battery level between 15 and 20 fot B2100 and between 12 and 19 for B5600. Scale accordingly to %
                Timber.i("battery level row value: ${bytes[0].toInt()}")

                val batteryLevelLowerLimit =
                    if (WatchInfo.model == WatchInfo.WATCH_MODEL.GA) 15 else 12
                val batteryLevelUpperLimit =
                    if (WatchInfo.model == WatchInfo.WATCH_MODEL.GA) 20 else 19
                val multiplier: Int =
                    (100.0 / (batteryLevelUpperLimit - batteryLevelLowerLimit)).roundToInt()
                val batteryLevel = (bytes[0].toInt() - batteryLevelLowerLimit)
                val batteryLevelPercent: Int = (batteryLevel * multiplier).coerceIn(0, 100)
                val temperature: Int = bytes[1].toInt()

                return WatchConditionValue(batteryLevelPercent, temperature)
            }

            return WatchConditionValue(0, 0)
        }
    }
}