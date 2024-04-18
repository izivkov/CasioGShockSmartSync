package org.avmedia.gshockapi

/**
 * This class keeps information about the characteristics of the currently connected watch.
 * Based on that, the application can display different information.
 */
object WatchInfo {
    enum class WATCH_MODEL {
        GA, GW, DW, GMW, GPR, GST, MSG, GB001, GBD, MRG_B5000, GCW_B5000, EQB, UNKNOWN
    }

    var name: String = ""
    var shortName = ""
    private var address: String = ""

    var model = WATCH_MODEL.UNKNOWN

    var worldCitiesCount = 2
    var dstCount = 3
    var alarmCount = 5
    var hasAutoLight = false
    var hasReminders = false
    var shortLightDuration = ""
    var longLightDuration = ""
    var weekLanguageSupported = true
    var worldCities = true
    var temperature = true

    //  Battery level between 15 and 20 for B2100 and between 9 and 19 for B5600. Scale accordingly to %
    var batteryLevelLowerLimit = 15
    var batteryLevelUpperLimit = 20

    /**
     * Info about the model.
     */
    data class ModelInfo(
        var model: WATCH_MODEL,
        var worldCitiesCount: Int,
        var dstCount: Int,
        var alarmCount: Int,
        var hasAutoLight: Boolean,
        var hasReminders: Boolean,
        var shortLightDuration: String,
        val longLightDuration: String,
        val weekLanguageSupported: Boolean = true,
        val worldCities: Boolean = true,
        val temperature: Boolean = true,
        val batteryLevelLowerLimit: Int = 15,
        val batteryLevelUpperLimit: Int = 20
    )

    // @formatter:off
    private val models = listOf (
        ModelInfo(WATCH_MODEL.GW, 6, 3, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "2s", longLightDuration = "4s", batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19),
        ModelInfo(WATCH_MODEL.MRG_B5000, 6, 3, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "2s", longLightDuration = "4s", batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19),
        ModelInfo(WATCH_MODEL.GCW_B5000, 6, 3, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "2s", longLightDuration = "4s", batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19),
        ModelInfo(WATCH_MODEL.GMW, 6, 3, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "2s", longLightDuration = "4s", batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19),
        ModelInfo(WATCH_MODEL.GST, 2, 1, 5, hasAutoLight = false, hasReminders = true, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WATCH_MODEL.GA, 2, 1, 5, hasAutoLight = false, hasReminders = true, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WATCH_MODEL.GB001, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WATCH_MODEL.MSG, 2, 1, 5, hasAutoLight = false, hasReminders = true, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WATCH_MODEL.GPR, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s", weekLanguageSupported = false),
        ModelInfo(WATCH_MODEL.DW, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WATCH_MODEL.GBD, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s", worldCities = false, temperature = false),
        ModelInfo(WATCH_MODEL.EQB, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s", worldCities = false, temperature = false),
        ModelInfo(WATCH_MODEL.UNKNOWN, 2, 1, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "1.5s", longLightDuration = "3s")
    )
    // @formatter:on

    private val modelMap = models.associateBy { it.model }

    /**
     * When we obtain the name of the watch from the BLE connection, we need to call this method.
     * From here, we can determine and set all the model's characteristics.
     */
    fun setNameAndModel(name: String) {
        this.name = name

        // name is like: CASIO GW-B5600
        val parts = this.name.split(" ")
        if (parts.size > 1) {
            shortName = parts[1]
        }

        // *** Order matters. Long names should be checked before short names ***
        model = when {
            shortName.startsWith("MRG-B5000") -> WATCH_MODEL.MRG_B5000
            shortName.startsWith("GCW-B5000") -> WATCH_MODEL.GCW_B5000
            shortName.startsWith("G-B001") -> WATCH_MODEL.GB001
            shortName.startsWith("GMW") -> WATCH_MODEL.GMW
            shortName.startsWith("GST") -> WATCH_MODEL.GST
            shortName.startsWith("GPR") -> WATCH_MODEL.GPR
            shortName.startsWith("MSG") -> WATCH_MODEL.MSG
            shortName.startsWith("GBD") -> WATCH_MODEL.GBD
            shortName.startsWith("EQB") -> WATCH_MODEL.EQB
            shortName.startsWith("GMB") -> WATCH_MODEL.GA
            shortName.startsWith("GA") -> WATCH_MODEL.GA
            shortName.startsWith("GB") -> WATCH_MODEL.GA
            shortName.startsWith("GW") -> WATCH_MODEL.GW
            shortName.startsWith("DW") -> WATCH_MODEL.DW
            else -> WATCH_MODEL.UNKNOWN
        }

        this.hasReminders = modelMap[model]!!.hasReminders
        this.hasAutoLight = modelMap[model]!!.hasAutoLight
        this.alarmCount = modelMap[model]!!.alarmCount
        this.worldCitiesCount = modelMap[model]!!.worldCitiesCount
        this.dstCount = modelMap[model]!!.dstCount
        this.shortLightDuration = modelMap[model]!!.shortLightDuration
        this.longLightDuration = modelMap[model]!!.longLightDuration
        this.weekLanguageSupported = modelMap[model]!!.weekLanguageSupported
        this.worldCities = modelMap[model]!!.worldCities
        this.temperature = modelMap[model]!!.temperature
        this.batteryLevelLowerLimit = modelMap[model]!!.batteryLevelLowerLimit
        this.batteryLevelUpperLimit = modelMap[model]!!.batteryLevelUpperLimit

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