package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import kotlin.experimental.and

object DstWatchStateIO {

    suspend fun request(state: CasioIO.DTS_STATE): String {
        return CachedIO.request("1d0${state.state}", ::getDSTWatchState) as String
    }

    private suspend fun getDSTWatchState(key: String): String {

        CasioIO.request(key)

        var deferredResult = CompletableDeferred<String>()
        CachedIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        CachedIO.subscribe("CASIO_DST_WATCH_STATE") { keyedData ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            CachedIO.resultQueue.dequeue(key)?.complete(data)
        }

        return deferredResult.await()
    }

    /*
    There are six clocks on the Casio GW-B5600
    0 is the main clock
    1-5 are the world clocks

    0x1d 00 01 DST0 DST1 TZ0A TZ0B TZ1A TZ1B ff ff ff ff ff
    0x1d 02 03 DST2 DST3 TZ2A TZ2B TZ3A TZ3B ff ff ff ff ff
    0x1d 04 05 DST4 DST5 TZ4A TZ4B TZ5A TZ5B ff ff ff ff ff
    DST: bitwise flags; bit0: DST on, bit1: DST auto
    */

    enum class DTS_VALUE {
        OFF,
        ON,
        AUTO,
        ON_AND_AUTO
    }

    suspend fun setDST(dstState: String, dst: DTS_VALUE) : String {
        val DST_OFF_MASK = 0b00
        val DST_ON_MASK = 0b01
        val DST_AUTO_MASK = 0b10

        val intArray = Utils.toIntArray(dstState)
        intArray[3] = 0
        when (dst) {
            DTS_VALUE.OFF -> intArray[3] = intArray[3] or DST_OFF_MASK
            DTS_VALUE.ON ->  intArray[3] = intArray[3] or DST_ON_MASK
            DTS_VALUE.ON_AND_AUTO ->  intArray[3] = intArray[3] or DST_ON_MASK or DST_AUTO_MASK
            else ->  intArray[3] =  intArray[3] or DST_AUTO_MASK
        }
        val newValue = Utils.byteArrayOfIntArray(intArray.toIntArray())
        return Utils.fromByteArrayToHexStrWithSpaces(newValue)
    }

    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", CachedIO.createKey(data)).put("value", data)
        json.put("CASIO_DST_WATCH_STATE", dataJson)
        return json
    }
}