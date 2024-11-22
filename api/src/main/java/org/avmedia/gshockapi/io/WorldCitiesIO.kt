package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

object WorldCitiesIO {

    private object DeferredValueHolder {
        lateinit var deferredResult: CompletableDeferred<String>
    }

    suspend fun request(cityNumber: Int): String {
        return CachedIO.request("1f0$cityNumber", ::getWorldCities) as String
    }

    private suspend fun getWorldCities(key: String): String {
        DeferredValueHolder.deferredResult = CompletableDeferred()
        IO.request(key)
        return DeferredValueHolder.deferredResult.await()
    }

    fun onReceived(data: String) {
        DeferredValueHolder.deferredResult.complete(data)
    }

    fun parseCity(timeZone: String): String? {
        val city = timeZone.split('/').lastOrNull()
        return city?.uppercase()?.replace('_', ' ')
    }

    fun encodeAndPad(city: String, cityIndex: Int): String {
        return ("1F" + "%02x".format(cityIndex) + Utils.toHexStr(city.take(18))
            .padEnd(36, '0')) // pad to 40 chars
    }
}