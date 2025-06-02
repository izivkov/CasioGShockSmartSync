package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils

object AppInfoIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(): String {
        return CachedIO.request("22") { key ->
            state = state.copy(deferredResult = CompletableDeferred())
            IO.request(key)
            state.deferredResult?.await() ?: ""
        }
    }

    fun onReceived(data: String) {
        // App info:
        // This is needed to re-enable button D (Lower-right) after the watch has been reset
        // or BLE has been cleared.
        // It is a hard-coded value, which is what the official app does as well.

        // If watch was reset, the app info will come as:
        // 0x22 FF FF FF FF FF FF FF FF FF FF 00
        // In this case, set it to the hardcoded value bellow, so 'D' button will work again.
        val appInfoCompactStr = Utils.toCompactString(data)

        if (appInfoCompactStr == "22FFFFFFFFFFFFFFFFFFFF00") {
            IO.writeCmd(GetSetMode.SET, "223488F4E5D5AFC829E06D02")
            // For the DW-H5600 we get: "22b1faea51bd2f085f461502"
        }

        state.deferredResult?.complete(data)
        state = State()
    }
}
