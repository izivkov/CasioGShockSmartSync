package org.avmedia.smartsync.homeassistant

import androidx.annotation.Keep
import com.google.gson.JsonParser

/** Something in Home Assistant a watch button press can run: an automation, a script or a scene. */
@Keep
data class HaAction(val kind: String = "", val entityId: String = "", val name: String = "") {
    /** The service to call, for example /api/services/script/turn_on. */
    val servicePath: String
        get() = when (kind) {
            KIND_AUTOMATION -> "/api/services/automation/trigger"
            KIND_SCRIPT -> "/api/services/script/turn_on"
            else -> "/api/services/scene/turn_on"
        }

    val body: String get() = com.google.gson.JsonObject().apply { addProperty("entity_id", entityId) }.toString()

    companion object {
        const val KIND_AUTOMATION = "automation"
        const val KIND_SCRIPT = "script"
        const val KIND_SCENE = "scene"
        private val KINDS = listOf(KIND_AUTOMATION, KIND_SCRIPT, KIND_SCENE)

        /** Reads GET /api/states: keeps automations, scripts and scenes, named by friendly_name, sorted by kind then name. */
        fun parseStates(body: String): List<HaAction> = runCatching {
            JsonParser.parseString(body).asJsonArray.mapNotNull { e ->
                val o = e.asJsonObject
                val id = o.get("entity_id")?.asString ?: return@mapNotNull null
                val kind = KINDS.firstOrNull { id.startsWith("$it.") } ?: return@mapNotNull null
                val friendly = o.getAsJsonObject("attributes")?.get("friendly_name")?.takeIf { it.isJsonPrimitive }?.asString
                HaAction(kind, id, friendly?.takeIf { it.isNotBlank() } ?: id)
            }.sortedWith(compareBy({ KINDS.indexOf(it.kind) }, { it.name.lowercase() }))
        }.getOrDefault(emptyList())
    }
}
