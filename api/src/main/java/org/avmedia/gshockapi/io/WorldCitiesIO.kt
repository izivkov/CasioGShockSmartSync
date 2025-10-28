package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

object WorldCitiesIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(cityNumber: Int): String =
        CachedIO.request("1f0$cityNumber") { key -> getWorldCities(key) }

    private suspend fun getWorldCities(key: String): String {
        state = state.copy(deferredResult = CompletableDeferred())
        IO.request(key)
        return state.deferredResult?.await() ?: ""
    }

    fun onReceived(data: String) {
        state.deferredResult?.complete(data)
        // state = State()
    }

    fun parseCity(timeZone: String): String? {
        val city = timeZone.split('/').lastOrNull()
        return city?.uppercase()?.replace('_', ' ')
    }

    fun encodeAndPad(city: String, cityIndex: Int): String {
        return ("1F" + "%02x".format(cityIndex) + Utils.toHexStr(city.take(18))
            .padEnd(36, '0'))
    }
}
