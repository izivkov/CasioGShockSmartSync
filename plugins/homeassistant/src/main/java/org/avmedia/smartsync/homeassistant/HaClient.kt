package org.avmedia.smartsync.homeassistant

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Result of one Home Assistant call. [retry] means WorkManager should try again later. */
sealed class HaResult(val message: String, val detail: String = "") {
    class Ok(message: String) : HaResult(message)
    class Retry(message: String) : HaResult(message)
    class Failed(message: String, detail: String = "") : HaResult(message, detail)
}

/**
 * Home Assistant mobile_app client. The URL and token are saved once ([saveSettings]); each watch address
 * gets its own registration the first time a job for it is sent ([send]), and only then. Android-free: the
 * transport and the store are passed in.
 */
class HaClient(
    private val transport: HaTransport,
    private val store: HaStore,
    private val sensors: List<HaSensor> = HaSensors.ALL,
    /** False until the owner confirmed the watch's name ([HaStore.confirmName]); nothing is registered before. */
    private val mayRegister: (deviceId: String) -> Boolean = { true },
) {
    /** Saves URL, token and the typed name and model, after GET /api/ shows that HA accepts them. Saves nothing on an error. */
    fun saveSettings(baseUrl: String, token: String, deviceName: String = "", model: String = ""): HaResult {
        val base = normalizeBase(baseUrl) ?: return HaResult.Failed("The URL must start with http:// or https://")
        if (token.isBlank()) return HaResult.Failed("The token is empty")
        val r = transport.get("$base/api/", token.trim())
        val check = HaConnectionCheck.classify(r)
        if (check != HaCheck.OK) return HaResult.Failed(HaConnectionCheck.message(check), HaConnectionCheck.detail(r))
        store.save(HaConfig(base, token.trim(), deviceName.trim(), model.trim()))
        return HaResult.Ok(HaConnectionCheck.message(HaCheck.OK))
    }

    /** Registers the watch [deviceId] when it has no registration yet. Never registers an address twice. */
    fun ensureRegistered(deviceId: String, registrationBody: String): HaResult = withWatchLock(deviceId) {
        val cfg = store.load() ?: return@withWatchLock HaResult.Failed("Home Assistant is not set up")
        // Read inside the lock: a job that ran before us has registered the watch already.
        if (store.loadRegistration(deviceId) != null) return@withWatchLock HaResult.Ok("Already registered")
        if (!mayRegister(deviceId)) return@withWatchLock HaResult.Failed(NAME_FIRST)
        when (val r = register(cfg, registrationBody)) {
            is Reg.Done -> {
                store.saveRegistration(deviceId, HaRegistration(r.webhookId, registrationBody))
                HaResult.Ok("Registered " + deviceNameOf(registrationBody))
            }
            is Reg.Refused -> r.result
        }
    }

    /**
     * Sends [job] for its watch. A watch without a registration is registered first. A changed name or model
     * goes out as update_registration. A deleted registration (410, or an empty answer that get_config confirms)
     * registers again once and retries.
     */
    fun send(job: HaJob): HaResult {
        if (job.deviceId.isBlank() || job.registration.isBlank()) return HaResult.Failed("The job has no watch")
        return withWatchLock(job.deviceId) { sendLocked(job) }
    }

    private fun sendLocked(sent: HaJob): HaResult {
        val cfg = store.load() ?: return HaResult.Failed("Home Assistant is not set up")
        (ensureRegistered(sent.deviceId, sent.registration) as? HaResult.Failed)?.let { return it }
        var reg = store.loadRegistration(sent.deviceId) ?: return HaResult.Failed("Home Assistant gave no webhook id")
        // device_name names the notify action: once registered it never changes (model and the rest may).
        val job = sent.copy(registration = HaPayloads.keepDeviceName(sent.registration, reg.registration))
        var again = false
        if (reg.registration != job.registration) {
            val hook = "${cfg.baseUrl}/api/webhook/${reg.webhookId}"
            val r = transport.post(hook, HaPayloads.updateRegistration(job.registration), null)
            if (!gone(r, hook)) {
                classify(r)?.let { return it }
                reg = reg.copy(registration = job.registration).also { store.saveRegistration(job.deviceId, it) }
            } else {
                again = true
                when (val n = register(cfg, job.registration)) {
                    is Reg.Done -> reg = HaRegistration(n.webhookId, job.registration).also { store.saveRegistration(job.deviceId, it) }
                    is Reg.Refused -> return n.result
                }
            }
        }
        while (true) {
            when (val outcome = push(cfg, reg, job)) {
                is Push.Finished -> return outcome.result
                is Push.NeedsRegistration -> {
                    if (again) return HaResult.Failed("Home Assistant keeps refusing the registration")
                    again = true
                    if (outcome.registrationGone) {
                        when (val r = register(cfg, job.registration)) {
                            is Reg.Done -> reg = HaRegistration(r.webhookId, job.registration, "").also { store.saveRegistration(job.deviceId, it) }
                            is Reg.Refused -> return r.result
                        }
                    } else {
                        // A sensor is unknown to HA: register the sensors again.
                        reg = reg.copy(sensorsHash = "").also { store.saveRegistration(job.deviceId, it) }
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

    private fun register(cfg: HaConfig, body: String): Reg {
        val r = transport.post("${cfg.baseUrl}/api/mobile_app/registrations", body, cfg.token)
        classify(r)?.let { return Reg.Refused(it) }
        val id = runCatching { JsonParser.parseString(r.body).asJsonObject.get("webhook_id").asString }.getOrNull()
        return if (id.isNullOrBlank()) Reg.Refused(HaResult.Failed("Home Assistant gave no webhook id")) else Reg.Done(id)
    }

    private fun push(cfg: HaConfig, reg: HaRegistration, given: HaJob): Push {
        val forWatch = HaSensors.forCaps(this.sensors, WatchCaps.of(given))
        // Health switched off: its sensors are disabled in HA and none of their values goes out.
        val healthOn = store.healthOn()
        val sensors = if (healthOn) forWatch else forWatch.filter { HaGroups.of(it) != HaGroup.HEALTH }
        val removed = forWatch - sensors.toSet()
        // Only the values of sensors this watch has: an OCW job never updates a health sensor.
        val ids = sensors.map { it.uniqueId }.toSet()
        val job = given.copy(states = given.states.filterKeys { it in ids }, attributes = given.attributes?.filterKeys { it in ids })
        val hook = "${cfg.baseUrl}/api/webhook/${reg.webhookId}"
        val fingerprint = HaSensors.fingerprint(sensors) + if (removed.isNotEmpty()) HEALTH_OFF else ""
        if (reg.sensorsHash != fingerprint) {
            // Back on after it was off: the health sensors are enabled again (only then, never one the person disabled).
            val enable = healthOn && store.healthRemoved(job.deviceId)
            // register_sensor carries a state and HA shows it: a null would turn a known value to unknown.
            // So: the value of this job, else the last one sent (persisted), else null for a sensor never sent.
            // The attributes come with the value they belong to: HA replaces them too (steps_by_hour).
            val lastSent = store.loadSent(job.deviceId)
            for (s in sensors) {
                val fresh = job.states[s.uniqueId]
                val state = fresh ?: lastSent.states[s.uniqueId]
                val attributes = if (fresh != null) job.attributes?.get(s.uniqueId) else lastSent.attributes?.get(s.uniqueId)
                val disabled = if (enable && HaGroups.of(s) == HaGroup.HEALTH) false else null
                val r = transport.post(hook, HaPayloads.registerSensor(s, state, attributes, disabled), null)
                if (gone(r, hook)) return Push.NeedsRegistration(true)
                classify(r)?.let { return Push.Finished(it) }
            }
            for (s in removed) {
                val r = transport.post(hook, HaPayloads.registerSensor(s, lastSent.states[s.uniqueId], disabled = true), null)
                if (gone(r, hook)) return Push.NeedsRegistration(true)
                classify(r)?.let { return Push.Finished(it) }
            }
            store.setHealthRemoved(job.deviceId, removed.isNotEmpty())
            store.saveRegistration(job.deviceId, reg.copy(sensorsHash = fingerprint))
        }
        if (job.states.isNotEmpty()) {
            val r = transport.post(hook, HaPayloads.updateSensorStates(job.states, sensors, job.attributes.orEmpty()), null)
            if (gone(r, hook)) return Push.NeedsRegistration(true)
            classify(r)?.let { return Push.Finished(it) }
            if (notRegistered(r.body)) return Push.NeedsRegistration(false)
        }
        job.event?.let {
            val r = transport.post(hook, HaPayloads.fireEvent(it), null)
            if (gone(r, hook)) return Push.NeedsRegistration(true)
            classify(r)?.let { res -> return Push.Finished(res) }
            // The event is out. The chosen action is extra: its failure is saved for the screen, never returned.
            HaActionRunner(transport, store).runChosen()
        }
        val what = "Sent ${job.states.size} sensor value(s)" + if (job.event != null) " and the button event" else ""
        return Push.Finished(HaResult.Ok(what))
    }

    /**
     * The registration was deleted in HA: HTTP 410, or an empty 200 that get_config (which always answers JSON
     * while the registration exists) confirms. An empty 200 alone is not enough: some live commands answer
     * with nothing, and a needless re-registration replaces the HA entry and drops its sensors.
     */
    private fun gone(r: HaResponse, hook: String): Boolean {
        if (r.status == 410) return true
        if (r.status == null || r.status !in 200..299 || r.body.isNotBlank()) return false
        val c = transport.post(hook, """{"type":"get_config"}""", null)
        return c.status == 410 || (c.status != null && c.status in 200..299 && c.body.isBlank())
    }

    /** Null for a 2xx answer; otherwise the failure, retryable or final. */
    private fun classify(r: HaResponse): HaResult? {
        val s = r.status
        return when {
            s != null && s in 200..299 -> null
            CallRetry.isRetryable(s) -> HaResult.Retry(if (s == null) "No response: ${r.body.take(100)}" else "HTTP $s")
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
        /** Shown when a watch would register before its name was confirmed on the Home Assistant screen. */
        /** Added to the sensors' fingerprint while health is off: switching it changes the registration. */
        const val HEALTH_OFF = "-health-off"
        const val NAME_FIRST = "Confirm the watch name on the Home Assistant screen first; nothing was registered"
        // One lock per watch for the whole process: HaWorker makes a new HaClient per run, and a replaced
        // worker's blocking HTTP call goes on after WorkManager cancelled it. Without this, jobs that overlap
        // each register the watch, and every new registration makes HA drop the sensors of the old one.
        private val locks = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>()

        private inline fun <T> withWatchLock(deviceId: String, block: () -> T): T {
            val lock = locks.getOrPut(deviceId) { java.util.concurrent.locks.ReentrantLock() }
            lock.lock()
            try { return block() } finally { lock.unlock() }
        }

        /** "https://ha.example/" gives "https://ha.example"; null when it is not an http(s) URL. */
        fun normalizeBase(url: String): String? =
            url.trim().trimEnd('/').takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
