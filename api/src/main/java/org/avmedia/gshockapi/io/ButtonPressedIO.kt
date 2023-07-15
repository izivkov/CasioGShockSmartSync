package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object ButtonPressedIO {

    suspend fun request(): CasioIO.WATCH_BUTTON {
        return CachedIO.request("10", ::getPressedButton) as CasioIO.WATCH_BUTTON
    }

    private suspend fun getPressedButton(key: String): CasioIO.WATCH_BUTTON {

        CasioIO.request(key)

        val deferredResultButton = CompletableDeferred<CasioIO.WATCH_BUTTON>()

        CachedIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResultButton as CompletableDeferred<Any>
            )
        )

        CachedIO.subscribe("BUTTON_PRESSED") { keyedData ->
            /*
            RIGHT BUTTON: 0x10 17 62 07 38 85 CD 7F ->04<- 03 0F FF FF FF FF 24 00 00 00
            LEFT BUTTON:  0x10 17 62 07 38 85 CD 7F ->01<- 03 0F FF FF FF FF 24 00 00 00
                          0x10 17 62 16 05 85 dd 7f ->00<- 03 0f ff ff ff ff 24 00 00 00 // after watch reset
            AUTO-TIME:    0x10 17 62 16 05 85 dd 7f ->03<- 03 0f ff ff ff ff 24 00 00 00 // no button pressed
            FIND PHONE:   0x10 07 7A 29 33 A1 C6 7F ->02<- 03 0F FF FF FF FF 24 00 00 00 // find phone
            */
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            var ret: CasioIO.WATCH_BUTTON = CasioIO.WATCH_BUTTON.INVALID

            if (data != "" && Utils.toIntArray(data).size >= 19) {
                val bleIntArr = Utils.toIntArray(data)
                ret = when (bleIntArr[8]) {
                    in 0..1 -> CasioIO.WATCH_BUTTON.LOWER_LEFT
                    2 -> CasioIO.WATCH_BUTTON.FIND_PHONE
                    4 -> CasioIO.WATCH_BUTTON.LOWER_RIGHT
                    3 -> CasioIO.WATCH_BUTTON.NO_BUTTON // auto time set, no button pressed. Run actions to set time and calender only.
                    else -> CasioIO.WATCH_BUTTON.INVALID
                }
            }

            CachedIO.resultQueue.dequeue(key)?.complete(ret)
        }

        return deferredResultButton.await()
    }

    fun get(): CasioIO.WATCH_BUTTON {
        return CachedIO.get("10") as CasioIO.WATCH_BUTTON
    }

    fun put(value: Any) {
        CachedIO.put("10", value)
    }

    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", CachedIO.createKey(data)).put("value", data)
        json.put("BUTTON_PRESSED", dataJson)
        return json
    }
}