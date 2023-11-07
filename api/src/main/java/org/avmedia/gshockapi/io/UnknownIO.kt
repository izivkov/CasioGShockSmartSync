package org.avmedia.gshockapi.io

import org.json.JSONObject

object UnknownIO {

    suspend fun request(): String {
        return "UNKNOWN"
    }

    fun onReceived(data: String) {
    }
}