package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Result of one Home Assistant call. [retry] means WorkManager should try again later. */
sealed class HaResult(val message: String) {
    class Ok(message: String) : HaResult(message)
    class Retry(message: String) : HaResult(message)
    class Failed(message: String) : HaResult(message)
}

/**
 * Registers the app as a Home Assistant mobile_app device and pushes sensor states and button
 * events to its webhook. Android-free: the transport and the store are passed in.
 */
class HaClient(
    private val transport: HaTransport,
    private val store: HaStore,
    private val sensors: List<HaSensor> = HaSensors.ALL,
) {
    /** Registers the device and keeps the webhook id. [registrationBody] is [HaPayloads.registration]. */
    fun connect(baseUrl: String, token: String, registrationBody: String): HaResult {
        val base = normalizeBase(baseUrl) ?: return HaResult.Failed("The URL must start with http:// or https://")
        if (token.isBlank()) return HaResult.Failed("The token is empty")
        val cfg = HaConfig(baseUrl = base, token = token.trim(), registration = registrationBody)
        store.save(cfg)
        return when (val r = register(cfg)) {
            is Reg.Done -> {
                store.save(cfg.copy(webhookId = r.webhookId))
                HaResult.Ok("Connected as " + deviceNameOf(registrationBody))
            }
            is Reg.Refused -> r.result
        }
    }

    /** Sends [job]. A 410 (registration deleted in HA) registers again once and retries. */
    fun send(job: HaJob): HaResult {
        var cfg = store.load() ?: return HaResult.Failed("Home Assistant is not connected")
        var again = false
        while (true) {
            when (val outcome = push(cfg, job)) {
                is Push.Finished -> return outcome.result
                is Push.NeedsRegistration -> {
                    if (again) return HaResult.Failed("Home Assistant keeps refusing the registration")
                    again = true
                    if (outcome.registrationGone) {
                        when (val r = register(cfg)) {
                            is Reg.Done -> cfg = cfg.copy(webhookId = r.webhookId, sensorsHash = "").also(store::save)
                            is Reg.Refused -> return r.result
                        }
                    } else {
                        // A sensor is unknown to HA: register the sensors again.
                        cfg = cfg.copy(sensorsHash = "").also(store::save)
                    }
                }
            }
        }
    }

    private sealed interface Reg {
        class Done(val webhookId: String) : Reg
        class Refused(val result: HaResult) : Reg
    }

    private sealed interface Push {
        class Finished(val result: HaResult) : Push
        class NeedsRegistration(val registrationGone: Boolean) : Push
    }

    private fun register(cfg: HaConfig): Reg {
        val r = transport.post("${cfg.baseUrl}/api/mobile_app/registrations", cfg.registration, cfg.token)
        classify(r)?.let { return Reg.Refused(it) }
        val id = runCatching { JsonParser.parseString(r.body).asJsonObject.get("webhook_id").asString }.getOrNull()
        return if (id.isNullOrBlank()) Reg.Refused(HaResult.Failed("Home Assistant gave no webhook id")) else Reg.Done(id)
    }

    private fun push(cfg: HaConfig, job: HaJob): Push {
        val hook = "${cfg.baseUrl}/api/webhook/${cfg.webhookId}"
        val fingerprint = HaSensors.fingerprint(sensors)
        if (cfg.sensorsHash != fingerprint) {
            for (s in sensors) {
                val r = transport.post(hook, HaPayloads.registerSensor(s, job.states[s.uniqueId], job.attributes?.get(s.uniqueId)), null)
                if (gone(r)) return Push.NeedsRegistration(true)
                classify(r)?.let { return Push.Finished(it) }
            }
            store.save(cfg.copy(sensorsHash = fingerprint))
        }
        if (job.states.isNotEmpty()) {
            val r = transport.post(hook, HaPayloads.updateSensorStates(job.states, sensors, job.attributes.orEmpty()), null)
            if (gone(r)) return Push.NeedsRegistration(true)
            classify(r)?.let { return Push.Finished(it) }
            if (notRegistered(r.body)) return Push.NeedsRegistration(false)
        }
        job.event?.let {
            val r = transport.post(hook, HaPayloads.fireEvent(it), null)
            if (gone(r)) return Push.NeedsRegistration(true)
            classify(r)?.let { res -> return Push.Finished(res) }
        }
        val what = "Sent ${job.states.size} sensor value(s)" + if (job.event != null) " and the button event" else ""
        return Push.Finished(HaResult.Ok(what))
    }

    /**
     * The registration was deleted in HA. The docs say HTTP 410; the real HA (checked 2026-11-22) answers
     * 200 with an empty body instead, while every live answer has a JSON body. Both mean: register again.
     */
    private fun gone(r: HaResponse): Boolean =
        r.status == 410 || (r.status != null && r.status in 200..299 && r.body.isBlank())

    /** Null for a 2xx answer; otherwise the failure, retryable or final. */
    private fun classify(r: HaResponse): HaResult? {
        val s = r.status
        return when {
            s != null && s in 200..299 -> null
            WebhookPolicy.isRetryable(s) -> HaResult.Retry(if (s == null) "No response: ${r.body.take(100)}" else "HTTP $s")
            s == 401 || s == 403 -> HaResult.Failed("HTTP $s: Home Assistant refused the token")
            else -> HaResult.Failed("HTTP $s")
        }
    }

    /** update_sensor_states answers 200 with one entry per sensor; a sensor HA lost says not_registered. */
    private fun notRegistered(body: String): Boolean = runCatching {
        JsonParser.parseString(body).asJsonObject.entrySet().any { (_, v) ->
            (v as? JsonObject)?.getAsJsonObject("error")?.get("code")?.asString == "not_registered"
        }
    }.getOrDefault(false)

    private fun deviceNameOf(registrationBody: String): String =
        runCatching { JsonParser.parseString(registrationBody).asJsonObject.get("device_name").asString }
            .getOrDefault("the watch")

    companion object {
        /** "https://ha.example/" gives "https://ha.example"; null when it is not an http(s) URL. */
        fun normalizeBase(url: String): String? =
            url.trim().trimEnd('/').takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
