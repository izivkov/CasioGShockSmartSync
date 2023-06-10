package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.utils.ResultQueue
import org.avmedia.gshockapi.utils.Utils

object ButtonPressedIO: ApiIO() {

    suspend fun request(): Any {
        val key = "10"

        // call directly the function, do not use the cache
        return getPressedButton(key)
    }

    private suspend fun getPressedButton(key: String): BluetoothWatch.WATCH_BUTTON {

        CasioIO.request(key)

        val deferredResultButton = CompletableDeferred<BluetoothWatch.WATCH_BUTTON>()

        resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResultButton as CompletableDeferred<Any>
            )
        )

        subscribe("BUTTON_PRESSED") { keyedData ->
            /*
            RIGHT BUTTON: 0x10 17 62 07 38 85 CD 7F ->04<- 03 0F FF FF FF FF 24 00 00 00
            LEFT BUTTON:  0x10 17 62 07 38 85 CD 7F ->01<- 03 0F FF FF FF FF 24 00 00 00
                          0x10 17 62 16 05 85 dd 7f ->00<- 03 0f ff ff ff ff 24 00 00 00 // after watch reset
            AUTO-TIME:    0x10 17 62 16 05 85 dd 7f ->03<- 03 0f ff ff ff ff 24 00 00 00 // no button pressed
            */
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            var ret: BluetoothWatch.WATCH_BUTTON = BluetoothWatch.WATCH_BUTTON.INVALID

            if (data != "" && Utils.toIntArray(data).size >= 19) {
                val bleIntArr = Utils.toIntArray(data)
                ret = when (bleIntArr[8]) {
                    in 0..1 -> BluetoothWatch.WATCH_BUTTON.LOWER_LEFT
                    4 -> BluetoothWatch.WATCH_BUTTON.LOWER_RIGHT
                    3 -> BluetoothWatch.WATCH_BUTTON.NO_BUTTON // auto time set, no button pressed. Run actions to set time and calender only.
                    else -> BluetoothWatch.WATCH_BUTTON.INVALID
                }
            }

            resultQueue.dequeue(key)?.complete(ret)
        }

        return deferredResultButton.await()
    }

    fun get (): BluetoothWatch.WATCH_BUTTON {
        return get("10") as BluetoothWatch.WATCH_BUTTON
    }
    fun put (value: Any) {
        put("10", value)
    }
}