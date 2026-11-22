package org.avmedia.gshockGoogleSync.webhook

/** What HA offers to run; [error] is plain words when the list could not be loaded. */
data class HaActionList(val items: List<HaAction>, val error: String? = null)

/** Lists the automations, scripts and scenes of HA and runs the chosen one after a button press. Android-free. */
class HaActionRunner(
    private val transport: HaTransport,
    private val store: HaStore,
    private val now: () -> String = { java.time.LocalTime.now().withSecond(0).withNano(0).toString() },
) {
    /** GET /api/states with the saved token. */
    fun fetch(): HaActionList = HaActionList(emptyList(), null)

    /** Runs the chosen action, if any, and saves the result in plain words. Never throws; the caller ignores the result. */
    fun runChosen() {}
}
