package org.avmedia.gshockapi

import org.avmedia.gshockapi.casio.CasioTimeZoneHelper

/**
 * This class keeps track of what is the model and name of the Watch.
 * Currently supported models are: B2100, B5600 (which also includes the B5000, but they have identical functionality)
 */
object WatchInfo {
    enum class WATCH_MODEL {
        GA, GW, DW, UNKNOWN
    }

    var model = WATCH_MODEL.UNKNOWN

    val worldCitiesCount by lazy {
        when (model) {
            WATCH_MODEL.GA -> 2
            WATCH_MODEL.GW -> 6
            WATCH_MODEL.DW -> 2
            else -> 0
        }
    }

    val dstCount by lazy {
        when (model) {
            WATCH_MODEL.GA -> 1
            WATCH_MODEL.GW -> 3
            WATCH_MODEL.DW -> 1
            else -> 1
        }
    }

    val alarmCount by lazy {
        when (model) {
            WATCH_MODEL.GA -> 5
            WATCH_MODEL.GW -> 5
            WATCH_MODEL.DW -> 5
            else -> 5
        }
    }

    val reminderCount by lazy {
        when (model) {
            WATCH_MODEL.GA -> 6
            WATCH_MODEL.GW -> 6
            WATCH_MODEL.DW -> 0
            else -> 6
        }
    }

    val hasAutoLight by lazy {
        when (model) {
            WATCH_MODEL.GW -> true
            WATCH_MODEL.GA -> false
            WATCH_MODEL.DW -> false
            else -> false
        }
    }

    val hasReminders by lazy {
        when (model) {
            WATCH_MODEL.GW -> true
            WATCH_MODEL.GA -> true
            WATCH_MODEL.DW -> false
            else -> false
        }
    }

    var name: String = ""
    var shortName = ""
    private var address: String = ""

    fun setNameAndModel(name: String) {
        this.name = name

        // name is like: CASIO GW-B5600
        val parts = this.name.split(" ")
        if (parts.size > 1) {
            shortName = parts[1]
        }

        model = when {
            shortName.startsWith("GA") -> WATCH_MODEL.GA
            shortName.startsWith("GW") -> WATCH_MODEL.GW
            shortName.startsWith("DW") -> WATCH_MODEL.DW
            else -> WATCH_MODEL.UNKNOWN
        }

        ProgressEvents.onNext("DeviceName", this.name)
    }

    fun setAddress(address: String) {
        this.address = address
        ProgressEvents.onNext("DeviceAddress", address)
    }

    fun getAddress(): String {
        return address
    }

    fun reset() {
        this.address = ""
        this.name = ""
        this.shortName = ""
        this.model = WATCH_MODEL.UNKNOWN
    }
}