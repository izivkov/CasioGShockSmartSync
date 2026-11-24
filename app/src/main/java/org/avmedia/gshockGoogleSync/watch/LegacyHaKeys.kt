package org.avmedia.gshockGoogleSync.watch

import com.google.gson.Gson
import com.google.gson.JsonParser

/**
 * The Home Assistant registrations as versions before the plugin interface stored them ("ha.regs", "ha.reg.<id>"),
 * read only to place what older versions kept for one watch: the per-watch values ([WatchStore] legacy owner) and the
 * Keep connected migration. A frozen format: the Home Assistant plugin keeps writing it, the app never does.
 */
object LegacyHaKeys {
    private val gson = Gson()

    fun registeredIds(kv: KeyValueStore): List<String> =
        runCatching { gson.fromJson(kv.get("ha.regs"), Array<String>::class.java)?.toList() }.getOrNull().orEmpty()

    private fun model(kv: KeyValueStore, id: String): String? = runCatching {
        val reg = JsonParser.parseString(kv.get("ha.reg.$id")).asJsonObject
        if (reg.get("webhookId")?.asString.isNullOrBlank()) return null
        JsonParser.parseString(reg.get("registration").asString).asJsonObject.get("model")?.asString
    }.getOrNull()

    /** The registered watch of the H5600 family (the one that had health data), else the only registered one. */
    fun legacyOwnerCandidate(kv: KeyValueStore): String? {
        val ids = registeredIds(kv)
        return ids.firstOrNull { id -> model(kv, id)?.contains("H5600", ignoreCase = true) == true } ?: ids.singleOrNull()
    }
}
