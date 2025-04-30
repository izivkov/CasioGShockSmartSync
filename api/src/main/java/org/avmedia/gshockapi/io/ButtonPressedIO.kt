package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

object ButtonPressedIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<IO.WatchButton>
    }

    suspend fun request(): IO.WatchButton {
        return CachedIO.request("10") { key ->
            getPressedButton(key)
        }
    }

    private suspend fun getPressedButton(key: String): IO.WatchButton {
        DeferredValueHolder.deferredResult = CompletableDeferred()
        IO.request(key)
        return DeferredValueHolder.deferredResult.await()
    }

    fun get(): IO.WatchButton {
        return CachedIO.get("10") as IO.WatchButton
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

        var ret: IO.WatchButton = IO.WatchButton.INVALID
        val AUTO_CONNECT_MASK = 0b00001000

        if (data != "" && Utils.toIntArray(data).size >= 19) {
            val bleIntArr = Utils.toIntArray(data)

            println("=============>>> Button pressed: dec=${bleIntArr[8]}, hex=0x${bleIntArr[8].toString(16)}, bin=${bleIntArr[8].toString(2).padStart(8, '0')}")

            ret = when {
                (bleIntArr[8] and AUTO_CONNECT_MASK) == AUTO_CONNECT_MASK -> IO.WatchButton.ALLAYS_CONNECTED_CONNECTION
                bleIntArr[8] in 0..1 -> IO.WatchButton.LOWER_LEFT
                bleIntArr[8] == 2 || bleIntArr[8] == 0xB -> IO.WatchButton.FIND_PHONE
                bleIntArr[8] == 4 -> IO.WatchButton.LOWER_RIGHT
                bleIntArr[8] == 3 -> IO.WatchButton.NO_BUTTON
                else -> IO.WatchButton.LOWER_LEFT
            }
        }

        DeferredValueHolder.deferredResult.complete(ret)
    }
}