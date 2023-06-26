package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.casio.CasioTimeZone
import org.avmedia.gshockapi.utils.Utils

object HomeTimeIO {

    suspend fun request(): String {
        val homeCityRaw = WorldCitiesIO.request(0)
        return Utils.toAsciiString(homeCityRaw, 2)
    }

    fun set(id: String) {
        CasioTimeZone.setHomeTime(id)
        CachedIO.cache.remove("1F00")
    }
}