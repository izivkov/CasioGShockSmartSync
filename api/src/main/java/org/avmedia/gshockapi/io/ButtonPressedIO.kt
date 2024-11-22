package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

object ButtonPressedIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<IO.WATCH_BUTTON>
    }

    suspend fun request(): IO.WATCH_BUTTON {
        return CachedIO.request("10", ::getPressedButton) as IO.WATCH_BUTTON
    }

    private suspend fun getPressedButton(key: String): IO.WATCH_BUTTON {
        DeferredValueHolder.deferredResult = CompletableDeferred()
        IO.request(key)
        return DeferredValueHolder.deferredResult.await()
    }

    fun get(): IO.WATCH_BUTTON {
        return CachedIO.get("10") as IO.WATCH_BUTTON
    }

    fun put(value: Any) {
        CachedIO.put("10", value)
    }

    fun onReceived(data: String) {

        /*
        RIGHT BUTTON: 0x10 17 62 07 38 85 CD 7F ->04<- 03 0F FF FF FF FF 24 00 00 00
        LEFT BUTTON:  0x10 17 62 07 38 85 CD 7F ->01<- 03 0F FF FF FF FF 24 00 00 00
                      0x10 17 62 16 05 85 dd 7f ->00<- 03 0f ff ff ff ff 24 00 00 00 // after watch reset
        AUTO-TIME:    0x10 17 62 16 05 85 dd 7f ->03<- 03 0f ff ff ff ff 24 00 00 00 // no button pressed
        FIND PHONE:   0x10 07 7A 29 33 A1 C6 7F ->02<- 03 0F FF FF FF FF 24 00 00 00 // find phone
        */

        var ret: IO.WATCH_BUTTON = IO.WATCH_BUTTON.INVALID

        if (data != "" && Utils.toIntArray(data).size >= 19) {
            val bleIntArr = Utils.toIntArray(data)
            ret = when (bleIntArr[8]) {
                in 0..1 -> IO.WATCH_BUTTON.LOWER_LEFT
                2 -> IO.WATCH_BUTTON.FIND_PHONE
                4 -> IO.WATCH_BUTTON.LOWER_RIGHT
                3 -> IO.WATCH_BUTTON.NO_BUTTON // auto time set, no button pressed. Run actions to set time and calender only.

                // For ECB-30 Possible values: 10, 0xE, 0xB

                else -> IO.WATCH_BUTTON.LOWER_LEFT
            }
        }
        DeferredValueHolder.deferredResult.complete(ret)
    }
}