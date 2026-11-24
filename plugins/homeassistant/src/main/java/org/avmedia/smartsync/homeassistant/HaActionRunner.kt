package org.avmedia.smartsync.homeassistant

/** What HA offers to run; [error] is plain words when the list could not be loaded. */
data class HaActionList(val items: List<HaAction>, val error: String? = null)

/** Lists the automations, scripts and scenes of HA and runs the chosen one after a button press. Android-free. */
class HaActionRunner(
    private val transport: HaTransport,
    private val store: HaStore,
    private val now: () -> String = { java.time.LocalTime.now().withSecond(0).withNano(0).toString() },
) {
    /** GET /api/states with the saved token. */
    fun fetch(): HaActionList {
        val cfg = store.load() ?: return HaActionList(emptyList(), "Save the Home Assistant setup first")
        val r = transport.get("${cfg.baseUrl}/api/states", cfg.token)
        failure(r, "the list")?.let { return HaActionList(emptyList(), it) }
        return HaActionList(HaAction.parseStates(r.body))
    }

    /** Runs the chosen action, if any, and saves the result in plain words. Never throws; the caller ignores the result. */
    fun runChosen() {
        val action = store.loadAction() ?: return
        val text = try {
            val cfg = store.load() ?: return
            val r = transport.post(cfg.baseUrl + action.servicePath, action.body, cfg.token)
            failure(r, action.name) ?: "Ran ${action.name} at ${now()}"
        } catch (e: Throwable) {
            "Could not run ${action.name}: ${Diagnostics.oneLine(e)}"
        }
        store.saveActionResult(text)
    }

    private fun failure(r: HaResponse, what: String): String? {
        val s = r.status
        return when {
            s != null && s in 200..299 -> null
            s == null -> "Could not reach Home Assistant for $what"
            s == 401 || s == 403 -> "Home Assistant refused the token, so $what was not run"
            s == 404 -> "Home Assistant could not find $what"
            else -> "Home Assistant answered HTTP $s for $what"
        }
    }
}
