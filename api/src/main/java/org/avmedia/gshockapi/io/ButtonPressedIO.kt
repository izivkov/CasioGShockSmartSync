package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

object ButtonPressedIO {
    private data class State(
        val lastKnownButton: IO.WatchButton = IO.WatchButton.INVALID,
        val deferredResult: CompletableDeferred<IO.WatchButton>? = null
    )

    private var state = State()

    suspend fun request(): IO.WatchButton {
        return CachedIO.request("10") { key ->
            state = state.copy(deferredResult = CompletableDeferred())
            IO.request(key)
            state.deferredResult?.await() ?: IO.WatchButton.INVALID
        }.also { button ->
            state = state.copy(lastKnownButton = button)
        }
    }

    fun get(): IO.WatchButton = state.lastKnownButton

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
        /*
        RIGHT BUTTON: 0x10 17 62 07 38 85 CD 7F ->04<- 03 0F FF FF FF FF 24 00 00 00
        LEFT BUTTON:  0x10 17 62 07 38 85 CD 7F ->01<- 03 0F FF FF FF FF 24 00 00 00
                      0x10 17 62 16 05 85 dd 7f ->00<- 03 0f ff ff ff ff 24 00 00 00 // after watch reset
        AUTO-TIME:    0x10 17 62 16 05 85 dd 7f ->03<- 03 0f ff ff ff ff 24 00 00 00 // no button pressed
        FIND PHONE:   0x10 07 7A 29 33 A1 C6 7F ->02<- 03 0F FF FF FF FF 24 00 00 00 // find phone
        */
        val button = parseButtonPress(data)
        state.deferredResult?.complete(button)
        state = State()
    }

    private fun parseButtonPress(data: String): IO.WatchButton {
        if (data.isEmpty() || Utils.toIntArray(data).size < 19) {
            return IO.WatchButton.INVALID
        }

        val bleIntArr = Utils.toIntArray(data)
        val pressedButton = bleIntArr[8]

        return when (pressedButton) {
            in 0..1 -> IO.WatchButton.LOWER_LEFT
            2 -> IO.WatchButton.FIND_PHONE
            4 -> IO.WatchButton.LOWER_RIGHT
            3 -> IO.WatchButton.NO_BUTTON // auto time set, no button pressed. Run actions to set time and calender only.
            else -> {
                // For Always-connected watches, Possible values: 0xA, 0xB, 0xD, 0xE,
                // Check if the number has the 0b1000 bit set, i.e any of 0xA, 0xE, 0xD, 0xB
                val alwaysRunningMask = 0b1000
                if (pressedButton and alwaysRunningMask != 0) {
                    IO.WatchButton.ALLAYS_CONNECTED_CONNECTION
                } else {
                    IO.WatchButton.LOWER_LEFT
                }
            }
        }
    }
}
