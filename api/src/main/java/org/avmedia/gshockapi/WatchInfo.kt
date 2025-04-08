package org.avmedia.gshockapi

/**
 * This class keeps information about the characteristics of the currently connected watch.
 * Based on that, the application can display different information.
 */
object WatchInfo {
    enum class WatchModel {
        GA, GW, DW, GMW, GPR, GST, MSG, GB001, GBD, MRG_B5000, GCW_B5000, EQB, ECB, ABL_100, UNKNOWN,
    }

    var name: String = ""
    var shortName = ""
    private var address: String = ""

    var model = WatchModel.UNKNOWN

    var worldCitiesCount = 2
    var dstCount = 3
    private var alarmCount = 5
    var hasAutoLight = false
    var hasReminders = false
    var shortLightDuration = "2s"
    var longLightDuration = "4s"
    var weekLanguageSupported = true
    var worldCities = true
    var hasTemperature = true
    var hasBatteryLevel: Boolean = true

    //  Battery level between 15 and 20 for B2100 and between 9 and 19 for B5600. Scale accordingly to %
    var batteryLevelLowerLimit = 15
    var batteryLevelUpperLimit = 20

    var alwaysConnected = false
    var findButtonUserDefined = false
    var hasPowerSavingMode = true

    /**
     * Info about the model.
     */
    data class ModelInfo(
        var model: WatchModel,
        var worldCitiesCount: Int,
        var dstCount: Int,
        var alarmCount: Int,
        var hasAutoLight: Boolean,
        var hasReminders: Boolean,
        var shortLightDuration: String,
        val longLightDuration: String,
        val weekLanguageSupported: Boolean = true,
        val worldCities: Boolean = true,
        val hasBatteryLevel: Boolean = true,
        val hasTemperature: Boolean = true,
        val batteryLevelLowerLimit: Int = 15,
        val batteryLevelUpperLimit: Int = 20,
        val alwaysConnected: Boolean = false,
        val findButtonUserDefined: Boolean = false,
        val hasPowerSavingMode: Boolean = true,
    )

    // @formatter:off
    private val models = listOf (
        ModelInfo(WatchModel.GW, 6, 3, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "2s", longLightDuration = "4s", batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19),
        ModelInfo(WatchModel.MRG_B5000, 6, 3, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "2s", longLightDuration = "4s", batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19),
        ModelInfo(WatchModel.GCW_B5000, 6, 3, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "2s", longLightDuration = "4s", batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19),
        ModelInfo(WatchModel.GMW, 6, 3, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "2s", longLightDuration = "4s", batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19),
        ModelInfo(WatchModel.GST, 2, 1, 5, hasAutoLight = false, hasReminders = true, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WatchModel.ABL_100, 2, 1, 5, hasAutoLight = false, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s", hasTemperature = false, hasBatteryLevel = false),
        ModelInfo(WatchModel.GA, 2, 1, 5, hasAutoLight = false, hasReminders = true, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WatchModel.GB001, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WatchModel.MSG, 2, 1, 5, hasAutoLight = false, hasReminders = true, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WatchModel.GPR, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s", weekLanguageSupported = false),
        ModelInfo(WatchModel.DW, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s"),
        ModelInfo(WatchModel.GBD, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s", worldCities = false, hasTemperature = false),
        ModelInfo(WatchModel.EQB, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s", worldCities = false, hasTemperature = false),
        ModelInfo(WatchModel.ECB, 2, 1, 5, hasAutoLight = true, hasReminders = false, shortLightDuration = "1.5s", longLightDuration = "3s", worldCities = true, hasTemperature = false, hasBatteryLevel = false,
            alwaysConnected = true, findButtonUserDefined=true, hasPowerSavingMode=false),
        ModelInfo(WatchModel.UNKNOWN, 2, 1, 5, hasAutoLight = true, hasReminders = true, shortLightDuration = "1.5s", longLightDuration = "3s")
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
            shortName.startsWith("MRG-B5000") -> WatchModel.MRG_B5000
            shortName.startsWith("GCW-B5000") -> WatchModel.GCW_B5000
            shortName.startsWith("ABL-100") -> WatchModel.ABL_100
            shortName.startsWith("G-B001") -> WatchModel.GB001
            shortName.startsWith("GMW") -> WatchModel.GMW
            shortName.startsWith("GST") -> WatchModel.GST
            shortName.startsWith("GPR") -> WatchModel.GPR
            shortName.startsWith("MSG") -> WatchModel.MSG
            shortName.startsWith("GBD") -> WatchModel.GBD
            shortName.startsWith("EQB") -> WatchModel.EQB
            shortName.startsWith("GMB") -> WatchModel.GA
            shortName == "ECB-10" || shortName == "ECB-20" || shortName == "ECB-30" -> WatchModel.ECB
            shortName.startsWith("GA") -> WatchModel.GA
            shortName.startsWith("GB") -> WatchModel.GA
            shortName.startsWith("GW") -> WatchModel.GW
            shortName.startsWith("DW") -> WatchModel.DW
            else -> WatchModel.UNKNOWN
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
        this.hasTemperature = modelMap[model]!!.hasTemperature
        this.batteryLevelLowerLimit = modelMap[model]!!.batteryLevelLowerLimit
        this.batteryLevelUpperLimit = modelMap[model]!!.batteryLevelUpperLimit
        this.alwaysConnected = modelMap[model]!!.alwaysConnected
        this.findButtonUserDefined = modelMap[model]!!.findButtonUserDefined
        this.hasPowerSavingMode = modelMap[model]!!.hasPowerSavingMode
        this.hasBatteryLevel = modelMap[model]!!.hasBatteryLevel

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
        this.model = WatchModel.UNKNOWN
    }
}