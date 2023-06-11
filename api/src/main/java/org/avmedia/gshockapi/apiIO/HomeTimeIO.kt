package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.casio.CasioTimeZone
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject

object HomeTimeIO {

    suspend fun request(): String {
        val homeCityRaw = WorldCitiesIO.request(0)
        return Utils.toAsciiString(homeCityRaw, 2)
    }

    fun set (id: String) {
        ApiIO.cache.remove("1f00")
        CasioTimeZone.setHomeTime(id)
    }
}