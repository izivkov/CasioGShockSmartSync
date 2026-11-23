package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.JsonParser

/**
 * What to do when the push socket is up again (and at the first connection). HA keeps no queue for the local push
 * channel, so a notification sent while the link was down is gone: the gap is logged once so the owner can see it.
 * Settings are read back through the mobile_app webhook (`render_template`, no extra token): when the helper
 * `input_text.gshock_settings` exists and holds a JSON object of registry keys that differs from the value applied
 * last, it goes through the same path as `command_watch_setting`. No helper: nothing happens, silently.
 */
class HaCatchUp(
    private val transport: HaTransport,
    private val store: HaStore,
    private val applySettings: (Map<String, Any?>) -> Unit = { WatchSettingCommands.shared?.submit(mapOf("settings" to it)) },
    private val log: (String) -> Unit,
    private val clock: (Long) -> String = { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0).toString() },
) {
    /** Blocking (HTTP): call it off the main thread. */
    fun run(deviceId: String, downSince: Long?, upAt: Long) {
        if (downSince != null) {
            log("Home Assistant: push link was down from ${clock(downSince)} to ${clock(upAt)}, notifications sent then are lost")
        }
        val cfg = store.load() ?: return
        val reg = store.loadRegistration(deviceId) ?: return
        val r = runCatching { transport.post("${cfg.baseUrl}/api/webhook/${reg.webhookId}", HaPayloads.renderTemplate(TEMPLATE), null) }.getOrNull() ?: return
        if (r.status == null || r.status !in 200..299) return
        val text = runCatching { JsonParser.parseString(r.body).asJsonObject.get("t").asString }.getOrNull()?.trim() ?: return
        if (text.isEmpty() || text.lowercase() in ABSENT) return
        
        store.saveAppliedSettings(text)
        val map = runCatching {
            JsonParser.parseString(text).asJsonObject.entrySet().associate { (k, v) ->
                k to (if (v.isJsonPrimitive) v.asJsonPrimitive.let { p -> if (p.isBoolean) p.asBoolean else if (p.isNumber) p.asDouble else p.asString } else v.toString())
            }
        }.getOrNull()
        if (map == null) {
            log("Home Assistant: gshock_settings is not a JSON object, ignored")
            return
        }
        if (map.isNotEmpty()) applySettings(map)
    }

    companion object {
        const val HELPER = "input_text.gshock_settings"
        const val TEMPLATE = "{{ states('$HELPER') }}"
        private val ABSENT = setOf("unknown", "unavailable", "none")
    }
}
