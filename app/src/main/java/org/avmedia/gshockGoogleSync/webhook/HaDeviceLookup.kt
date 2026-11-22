package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.GsonBuilder

/** Finds the Home Assistant device page id of a registered watch, to open its page. Best effort: null on any trouble. */
object HaDeviceLookup {
    private val gson = GsonBuilder().create()

    private const val TEMPLATE =
        "{% for d in integration_entities('mobile_app') | map('device_id') | unique | list %}" +
            "{{ d }}|{{ device_attr(d, 'name') }}|{{ device_attr(d, 'name_by_user') }}\n{% endfor %}"

    fun find(transport: HaTransport, cfg: HaConfig, deviceName: String): String? {
        val r = transport.post("${cfg.baseUrl}/api/template", gson.toJson(mapOf("template" to TEMPLATE)), cfg.token)
        if (r.status == null || r.status !in 200..299) return null
        return parse(r.body, deviceName)
    }

    /** Lines of "id|name|name_by_user"; the id of the line whose name is [deviceName]. */
    fun parse(answer: String, deviceName: String): String? =
        answer.lineSequence().map { it.split('|') }
            .firstOrNull { p -> p.size >= 3 && p[0].isNotBlank() && p.drop(1).any { it.trim().equals(deviceName.trim(), ignoreCase = true) } }
            ?.get(0)?.trim()

    fun deviceUrl(baseUrl: String, deviceId: String?): String =
        if (deviceId.isNullOrBlank()) "$baseUrl/config/devices/dashboard" else "$baseUrl/config/devices/device/$deviceId"
}
