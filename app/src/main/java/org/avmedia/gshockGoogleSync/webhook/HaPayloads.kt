package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/** The JSON bodies of the Home Assistant mobile_app API. Pure, so the shapes are unit tested. */
object HaPayloads {
    const val APP_ID = "org.avmedia.gshocksync.webhook"
    const val APP_NAME = "G-Shock Smart Sync"
    const val EVENT_TYPE = "gshock_button"

    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

    /** Stable for one watch address: "gshock-" + address without colons, lower case. */
    fun deviceId(address: String): String =
        "gshock-" + address.filter { it.isLetterOrDigit() }.lowercase()

    /** HA shows this as the device; the manufacturer is a separate field. */
    fun deviceName(watchName: String): String {
        val n = watchName.trim().ifBlank { "G-Shock" }
        return if (n.startsWith("CASIO", ignoreCase = true)) n else "CASIO $n"
    }

    /** The model without the maker: "CASIO GBX-H5600" gives "GBX-H5600". */
    fun defaultModel(watchName: String): String =
        watchName.trim().removePrefix("CASIO").trim().ifBlank { "G-Shock" }

    /** The device name and model to register: what the owner typed, or the defaults from the watch name. */
    fun resolveNames(watchName: String, typedName: String, typedModel: String): Pair<String, String> =
        Pair(typedName.trim().ifBlank { deviceName(watchName) }, typedModel.trim().ifBlank { defaultModel(watchName) })

    private val UPDATABLE = listOf("app_version", "device_name", "manufacturer", "model", "os_version")

    /** The mobile_app "update_registration" call, from a [registration] body: only the fields HA lets us change. */
    fun updateRegistration(registrationBody: String): String {
        val o = JsonParser.parseString(registrationBody).asJsonObject
        val data = linkedMapOf<String, Any?>()
        UPDATABLE.forEach { k -> o.get(k)?.takeIf { it.isJsonPrimitive }?.let { data[k] = it.asString } }
        return gson.toJson(linkedMapOf("type" to "update_registration", "data" to data))
    }

    fun registration(
        deviceId: String,
        deviceName: String,
        model: String,
        appVersion: String,
        osVersion: String,
    ): String = gson.toJson(
        linkedMapOf(
            "device_id" to deviceId,
            "app_id" to APP_ID,
            "app_name" to APP_NAME,
            "app_version" to appVersion,
            "device_name" to deviceName,
            "manufacturer" to "CASIO",
            "model" to model,
            "os_name" to "Android",
            "os_version" to osVersion,
            "supports_encryption" to false,
        )
    )

    fun registerSensor(s: HaSensor, state: Any?, attributes: Map<String, Any?>? = null): String = gson.toJson(
        linkedMapOf(
            "type" to "register_sensor",
            "data" to linkedMapOf<String, Any?>(
                "unique_id" to s.uniqueId,
                "name" to s.name,
                "type" to s.kind,
                "device_class" to s.deviceClass,
                "unit_of_measurement" to s.unit,
                "state_class" to s.stateClass,
                "icon" to s.icon,
                "entity_category" to s.entityCategory,
                "attributes" to attributes?.takeIf { it.isNotEmpty() },
            ).filterValues { it != null } + ("state" to state), // state is required, null when not known yet
        )
    )

    fun updateSensorStates(
        states: Map<String, Any>,
        sensors: List<HaSensor> = HaSensors.ALL,
        attributes: Map<String, Map<String, Any?>> = emptyMap(),
    ): String {
        val byId = sensors.associateBy { it.uniqueId }
        val data = states.map { (id, state) ->
            linkedMapOf<String, Any?>(
                "unique_id" to id,
                "state" to state,
                "type" to (byId[id]?.kind ?: "sensor"),
                "icon" to byId[id]?.icon,
                "attributes" to attributes[id]?.takeIf { it.isNotEmpty() },
            ).filterValues { it != null }
        }
        return gson.toJson(linkedMapOf("type" to "update_sensor_states", "data" to data))
    }

    fun fireEvent(eventData: Map<String, Any?>): String = gson.toJson(
        linkedMapOf(
            "type" to "fire_event",
            "data" to linkedMapOf("event_type" to EVENT_TYPE, "event_data" to eventData),
        )
    )
}
