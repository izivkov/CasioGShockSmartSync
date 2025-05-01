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

    /*
RIGHT BUTTON: 0x10 17 62 07 38 85 CD 7F ->04<- 03 0F FF FF FF FF 24 00 00 00
LEFT BUTTON:  0x10 17 62 07 38 85 CD 7F ->01<- 03 0F FF FF FF FF 24 00 00 00
              0x10 17 62 16 05 85 dd 7f ->00<- 03 0f ff ff ff ff 24 00 00 00 // after watch reset
AUTO-TIME:    0x10 17 62 16 05 85 dd 7f ->03<- 03 0f ff ff ff ff 24 00 00 00 // no button pressed
FIND PHONE:   0x10 07 7A 29 33 A1 C6 7F ->02<- 03 0F FF FF FF FF 24 00 00 00 // find phone
*/
    fun onReceived(data: String) {
        var ret: IO.WatchButton = IO.WatchButton.INVALID
        val LOWER_LEFT_INITIAL_VALUE = 0b0000
        val LOWER_LEFT_MASK = 0b0001
        val LOWER_RIGHT_MASK = 0b0100
        val NO_BUTTON_MASK = 0b0011
        val AUTO_CONNECT_MASK = 0b1000
        val FIND_PHONE_MASK = 0b0010

        if (data != "" && Utils.toIntArray(data).size >= 19) {
            val bleIntArr = Utils.toIntArray(data)
            val buttonValue = bleIntArr[8]

            println("=============>>> Button pressed: dec=$buttonValue, hex=0x${buttonValue.toString(16)}, bin=${buttonValue.toString(2).padStart(8, '0')}")

            ret = when {
                (buttonValue and AUTO_CONNECT_MASK) == AUTO_CONNECT_MASK -> IO.WatchButton.ALLAYS_CONNECTED_CONNECTION
                (buttonValue and FIND_PHONE_MASK) == FIND_PHONE_MASK -> IO.WatchButton.FIND_PHONE
                buttonValue == LOWER_LEFT_INITIAL_VALUE || (buttonValue and LOWER_LEFT_MASK) == LOWER_LEFT_MASK -> IO.WatchButton.LOWER_LEFT
                (buttonValue and LOWER_RIGHT_MASK) == LOWER_RIGHT_MASK -> IO.WatchButton.LOWER_RIGHT
                buttonValue == NO_BUTTON_MASK -> IO.WatchButton.NO_BUTTON
                else -> IO.WatchButton.LOWER_LEFT
            }
        }

        DeferredValueHolder.deferredResult.complete(ret)
    }
}