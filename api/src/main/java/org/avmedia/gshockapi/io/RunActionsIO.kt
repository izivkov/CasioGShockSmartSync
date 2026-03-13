package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo

object RunActionsIO {
    private data class State(
        val lastRequest: String = ""
    )

    private var state = State()

    suspend fun request(): String {
        state = state.copy(lastRequest = "RUN_ACTIONS")
        return state.lastRequest
    }

    fun onReceived(data: String) {
        data.takeIf { it == "0x0A 02" && WatchInfo.alwaysConnected }
            ?.let { ProgressEvents.onNext("RunActions") }
    }
}
