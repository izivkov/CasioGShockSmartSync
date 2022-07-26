package org.avmedia.gShockPhoneSync.casioB5600

import java.util.*

object CasioTimeZone {
    fun setHomeTime() {
        val city = CasioSupport.TimeZoneHelper.parseCity(TimeZone.getDefault().id)

        var worldCity = CasioSupport.WorldCity(city, 0)
        CasioSupport.writeCmdFromString(0xe, worldCity.createCasioString())
    }

    fun requestHomeTime() {
        CasioSupport.writeCmdFromString(0xC, "1f00")
    }
}