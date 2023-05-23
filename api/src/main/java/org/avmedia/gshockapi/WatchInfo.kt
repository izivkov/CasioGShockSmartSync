package org.avmedia.gshockapi

object WatchInfo {

    enum class WATCH_MODEL {
        B2100, B5600, UNKNOWN
    }

    var model = WATCH_MODEL.UNKNOWN
    private var deviceName: String = ""

    fun setDeviceName(name: String) {
        deviceName = name

        model = if (deviceName.contains("2100")) {
            WATCH_MODEL.B2100
        } else {
            WATCH_MODEL.B5600
        }
    }

    fun getDeviceName(): String {
        return deviceName
    }
}