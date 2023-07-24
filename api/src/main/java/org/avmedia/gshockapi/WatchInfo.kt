package org.avmedia.gshockapi

/**
 * This class keeps track of what is the model and name of the Watch.
 * Currently supported models are: B2100, B5600 (which also includes the B5000, but they have identical functionality)
 */
object WatchInfo {
    enum class WATCH_MODEL {
        B2100, B5600, UNKNOWN
    }

    var model = WATCH_MODEL.UNKNOWN
    private var name: String = ""
    private var address: String = ""

    fun setNameAndModel(name: String) {
        this.name = name
        model = when {
            name.contains("2100") -> WATCH_MODEL.B2100
            else -> WATCH_MODEL.B5600
        }

        ProgressEvents.onNext("DeviceName", name)
    }

    fun getName(): String = name

    fun setAddress(address: String) {
        this.address = address

        ProgressEvents.onNext("DeviceAddress", address)
    }

    fun getAddress(): String = address

    fun reset() {
        this.address = ""
        this.name = ""
    }
}