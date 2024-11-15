package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.ble.GET_SET_MODE
import org.avmedia.gshockapi.utils.Utils

object AppInfoIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<String>
    }

    suspend fun request(): String {
        return CachedIO.request("22", ::getAppInfo) as String
    }

    private suspend fun getAppInfo(key: String): String {
        DeferredValueHolder.deferredResult = CompletableDeferred()
        IO.request(key)
        return DeferredValueHolder.deferredResult.await()
    }

    fun onReceived(data: String) {

        fun setAppInfo(data: String): Unit {
            // App info:
            // This is needed to re-enable button D (Lower-right) after the watch has been reset or BLE has been cleared.
            // It is a hard-coded value, which is what the official app does as well.

            // If watch was reset, the app info will come as:
            // 0x22 FF FF FF FF FF FF FF FF FF FF 00
            // In this case, set it to the hardcoded value bellow, so 'D' button will work again.
            val appInfoCompactStr = Utils.toCompactString(data)
            if (appInfoCompactStr == "22FFFFFFFFFFFFFFFFFFFF00") {
                IO.writeCmd(GET_SET_MODE.SET, "223488F4E5D5AFC829E06D02")
            }
        }

        setAppInfo(data)
        DeferredValueHolder.deferredResult.complete(data)
    }
}