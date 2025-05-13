package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.utils.Utils

object AppInfoIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<String>
    }

    suspend fun request(): String {
        return CachedIO.request("22") { key -> getAppInfo(key) }
    }

    private suspend fun getAppInfo(key: String): String {
        DeferredValueHolder.deferredResult = CompletableDeferred()
        IO.request(key)
        return DeferredValueHolder.deferredResult.await()
    }

    fun onReceived(data: String) {

        println("Received data: $data")

        fun setAppInfo(data: String): Unit {
            // App info:
            // This is needed to re-enable button D (Lower-right) after the watch has been reset or BLE has been cleared.
            // It is a hard-coded value, which is what the official app does as well.

            // If watch was reset, the app info will come as:
            // 0x22 FF FF FF FF FF FF FF FF FF FF 00
            // In this case, set it to the hardcoded value bellow, so 'D' button will work again.
            val appInfoCompactStr = Utils.toCompactString(data)


            if (appInfoCompactStr == "22FFFFFFFFFFFFFFFFFFFF00") {
                IO.writeCmd(GetSetMode.SET, "223488F4E5D5AFC829E06D02")

                // For the DW-H5600 we get:       "22b1faea51bd2f085f461502"
                // IO.writeCmd(GetSetMode.SET, "22b1faea51bd2f085f461502")
            }
        }

        setAppInfo(data)
        DeferredValueHolder.deferredResult.complete(data)
    }
}