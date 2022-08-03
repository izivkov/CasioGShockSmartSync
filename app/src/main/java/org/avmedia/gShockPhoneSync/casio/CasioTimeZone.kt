package org.avmedia.gShockPhoneSync.casio

import org.avmedia.gShockPhoneSync.utils.Utils

object CasioTimeZone {
    class WorldCity(private val city: String, val index: Int) {
        fun createCasioString(): String {
            return ("1F" + "%02x".format(index) + Utils.toHexStr(city.take(18)).padEnd(40, '0'))
        }
    }

    object TimeZoneHelper {
        fun parseCity(timeZone: String): String {
            return try {
                val city = timeZone.split('/')[1]
                city.uppercase().replace('_', ' ')
            } catch (e: Error) {
                ""
            }
        }
    }

    fun setHomeTime(timeZone:String) {
        val city = TimeZoneHelper.parseCity(timeZone)

        var worldCity = WorldCity(city, 0)
        WatchFactory.watch.writeCmdFromString(0xe, worldCity.createCasioString())
    }

    fun rereadHomeTimeFromWatch() {
        WatchFactory.watch.writeCmdFromString(0xC, "1f00")
    }
}