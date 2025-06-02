package org.avmedia.gshockapi.io

import timber.log.Timber

object ErrorIO {
    private data class State(
        val error: String = ""
    )

    private var state = State()

    suspend fun request(): String = state.error.ifEmpty { "ERROR" }

    fun onReceived(data: String) {
        state = state.copy(error = data)
        Timber.d("ErrorIO: onReceived: $data")
    }
}
