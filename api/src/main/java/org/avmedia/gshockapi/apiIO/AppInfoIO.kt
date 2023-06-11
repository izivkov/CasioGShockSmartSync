package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.utils.Utils

object AppInfoIO {

    suspend fun request(): String {
        return ApiIO.request("22", ::getAppInfo) as String
    }

    private suspend fun getAppInfo(key: String): String {

        CasioIO.request(key)

        fun setAppInfo(data: String): Unit {
            // App info:
            // This is needed to re-enable button D (Lower-right) after the watch has been reset or BLE has been cleared.
            // It is a hard-coded value, which is what the official app does as well.

            // If watch was reset, the app info will come as:
            // 0x22 FF FF FF FF FF FF FF FF FF FF 00
            // In this case, set it to the hardcoded value bellow, so 'D' button will work again.
            val appInfoCompactStr = Utils.toCompactString(data)
            if (appInfoCompactStr == "22FFFFFFFFFFFFFFFFFFFF00") {
                CasioIO.writeCmd(0xE, "223488F4E5D5AFC829E06D02")
            }
        }

        var deferredResult = CompletableDeferred<String>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                key, deferredResult as CompletableDeferred<Any>
            )
        )

        ApiIO.subscribe("CASIO_APP_INFORMATION") { keyedData ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            ApiIO.resultQueue.dequeue(key)?.complete(data)
            setAppInfo(data)
        }

        return deferredResult.await()
    }
}