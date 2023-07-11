package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object UnknownIO {

    suspend fun request(): String {
        return "UNKNOWN"
    }

    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", "UNKNOWN").put("value", data)
        json.put("UNKNOWN", dataJson)
        return json
    }
}