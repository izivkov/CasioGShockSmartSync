package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object BatteryLevelIO {

    suspend fun request(): String {
        return CachedIO.request("28", ::getBatteryLevel) as String
    }

    private suspend fun getBatteryLevel(key: String): String {

        CasioIO.request(key)

        val deferredResult = CompletableDeferred<String>()
        CachedIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        CachedIO.subscribe("CASIO_WATCH_CONDITION") { keyedData: JSONObject ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            CachedIO.resultQueue.dequeue(key)?.complete(BatteryLevelDecoder.decodeValue(data))
        }

        return deferredResult.await()
    }

    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", CachedIO.createKey(data)).put("value", data)
        json.put("CASIO_WATCH_CONDITION", dataJson)
        return json
    }

    object BatteryLevelDecoder {

        fun decodeValue(data: String): String {
            var percent = 0

            var cmdInts = Utils.toIntArray(data)
            // command looks like 0x28 13 1E 00.
            // 50% level is obtain from the second Int 13:
            // 0x13 = 0b00010011
            // take MSB 0b0001. If it is 1, we have 50% charge
            val MASK_50_PERCENT = 0b00010000
            percent += if (cmdInts[1] or MASK_50_PERCENT != 0) 50 else 0

            // Fine value is obtained from the 3rd integer, 0x1E. The LSB (0xE) represents
            // the fine value between 0 and 0xf, which is the other 50%. So, to
            // get this value, we have 50% * 0xe / 0xf. We add this to the previous battery level.
            // So, for command 0x28 13 1E 00, our battery level would be:
            // 50% (from 0x13) + 47 = 97%
            // The 47 number was obtained from 50 * 0xe / 0xf or 50 * 14/15 = 46.66

            val MASK_FINE_VALUE = 0xf
            val fineValue = cmdInts[2] and MASK_FINE_VALUE
            percent += 50 * fineValue / 15

            return percent.toString()
        }
    }
}