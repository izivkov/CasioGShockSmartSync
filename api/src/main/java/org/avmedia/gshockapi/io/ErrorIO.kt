package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object ErrorIO {

    suspend fun request(): String {
        return "ERROR"
    }

    fun toJson(data: String): JSONObject {
        val json = JSONObject()
        val dataJson = JSONObject().put("key", "ERROR").put("value", data)
        json.put("ERROR", dataJson)
        return json
    }
}