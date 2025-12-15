package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber
import kotlin.math.roundToInt

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
        state.deferredResult?.complete(WatchConditionDecoder.decodeValue(data))
        state = State()
    }

    object WatchConditionDecoder {
        fun decodeValue(data: String): WatchConditionValue {
            val intArr = Utils.toIntArray(data)
            val bytes = Utils.byteArrayOfIntArray(intArr.drop(1).toIntArray())

            if (bytes.size >= 2) {
                Timber.i("battery level row value: ${bytes[0].toInt()}")

                val batteryLevelLowerLimit = WatchInfo.batteryLevelLowerLimit
                val batteryLevelUpperLimit = WatchInfo.batteryLevelUpperLimit
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
