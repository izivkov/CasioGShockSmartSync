package org.avmedia.gshockapi.io

object UnknownIO {

    suspend fun request(): String {
        return "UNKNOWN"
    }

    @Suppress("UNUSED_PARAMETER")
    fun onReceived(message: String) {
    }
}