package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

object WatchNameIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(): String =
        CachedIO.request("23") { key -> getWatchName(key) }

    private suspend fun getWatchName(key: String): String {
        state = state.copy(deferredResult = CompletableDeferred())
        IO.request(key)
        return state.deferredResult?.await() ?: ""
    }

    fun onReceived(data: String) {
        val watchName = Utils.trimNonAsciiCharacters(Utils.toAsciiString(data, 1))
        state.deferredResult?.complete(watchName)
        state = State()
    }
}
