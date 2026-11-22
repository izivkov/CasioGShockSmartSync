package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.health.H5600Session
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import timber.log.Timber

/**
 * Fires webhooks. Callers: the "Call webhooks" action (trigger watch_action), the connection
 * runner (trigger sync), and in the future the health download (trigger health_data).
 */
@Singleton
class WebhookDispatcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: GShockRepository,
    features: IWatchFeatureManager,
    raw: RawWatchRequest,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // The point where a DW-H5600 LifeLog read completes: the "health data downloaded" trigger.
        H5600Session.onDownloaded = { onHealthDataDownloaded() }
        // Home Assistant's "command_update_sensors" push: send the sensors once, nothing else.
        HaPushRouter.updateSensorsHook = { pushSensorsNow() }
    }

    val registry: VariableRegistry = WatchVariables.registry(WatchVariables.androidEnv(context, api, features, raw))

    /** Records "now" as the last connection, then fires the webhooks that want [WebhookTrigger.SYNC]. */
    fun onSync() {
        LocalDataStorage.put(context, WebhookStorage.KEY_LAST_SYNC, OffsetDateTime.now().withNano(0).toString())
        fire(WebhookTrigger.SYNC)
    }

    /**
     * Hook for the future "health data downloaded" trigger: call this once the health screen or
     * a background job has finished downloading data from the watch. Nothing calls it yet.
     */
    fun onHealthDataDownloaded() = fire(WebhookTrigger.HEALTH_DATA)

    /** One Home Assistant sensor push on request of HA. Uses the values already known: no LifeLog read is started. */
    fun pushSensorsNow() {
        if (!WebhookStorage.toggles(context).isOn(ServiceToggles.Service.HOME_ASSISTANT)) return
        scope.launch { runCatching { pushToHomeAssistant(WebhookTrigger.SYNC) }.onFailure { Timber.e(it, "Home Assistant push failed") } }
    }

    fun fire(trigger: WebhookTrigger) {
        val toggles = WebhookStorage.toggles(context)
        if (toggles.isOn(ServiceToggles.Service.HOME_ASSISTANT)) {
            scope.launch { runCatching { pushToHomeAssistant(trigger) }.onFailure { Timber.e(it, "Home Assistant push failed") } }
        }
        if (!toggles.isOn(ServiceToggles.Service.WEBHOOKS)) return
        scope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                WebhookStorage.loadHooks(context)
                    .filter { WebhookPolicy.shouldFire(it, trigger, WebhookStorage.lastAttempt(context, it.id), now) }
                    .forEach { hook ->
                        WebhookStorage.markAttempt(context, hook.id, now)
                        val rendered = render(hook, trigger)
                        WebhookStorage.putPending(context, hook.id, rendered.text)
                        WebhookWorker.enqueue(context, hook.id)
                    }
            }.onFailure { Timber.e(it, "Webhook dispatch failed") }
        }
    }

    /** Home Assistant: the sensor values (one registry read) and, for a watch action, the button event. */
    private suspend fun pushToHomeAssistant(trigger: WebhookTrigger) {
        val store = WebhookStorage.haStore(context)
        val cfg = store.load() ?: return
        val address = org.avmedia.gshockapi.WatchInfo.getAddress()
        if (address.isBlank()) return
        val watchName = org.avmedia.gshockGoogleSync.session.WatchModelOverride.displayName(org.avmedia.gshockapi.WatchInfo.name)
        val now = OffsetDateTime.now().withNano(0)
        val ctx = VariableContext(trigger.eventName, now, runCatching { api.isConnected() }.getOrDefault(false))
        val sensors = HaGroups.sensors(store.groupsOn())
        val values = registry.resolve(ctx, HaSensors.variablesOf(sensors))
        val states = HaSensors.states(values, sensors)
        val event = if (trigger == WebhookTrigger.WATCH_ACTION) {
            mapOf("trigger" to trigger.eventName, "time" to now.toString(),
                "watch" to org.avmedia.gshockGoogleSync.session.WatchModelOverride.displayName(org.avmedia.gshockapi.WatchInfo.name))
        } else null
        if (states.isEmpty() && event == null) return
        val registration = HaPayloads.registrationFor(
            address, watchName, cfg, org.avmedia.gshockGoogleSync.BuildConfig.VERSION_NAME, android.os.Build.VERSION.RELEASE,
        )
        store.putPending(HaJob(states, event, HaSensors.attributes(values, sensors), HaPayloads.deviceId(address), registration))
        HaWorker.enqueue(context)
    }

    /** Renders [hook] with the current values. [hook] may be an unsaved edit (Test button). */
    suspend fun render(hook: Webhook, trigger: WebhookTrigger): TemplateRenderer.Rendered {
        val ctx = VariableContext(trigger.eventName, OffsetDateTime.now(), runCatching { api.isConnected() }.getOrDefault(false))
        return registry.render(hook.bodyTemplate, hook.isJson, ctx)
    }

    /** Values the phone and app know right now, for the editor preview. Watch values need a connection and are left out. */
    suspend fun sampleValues(): Map<String, Any?> =
        registry.resolve(VariableContext(WebhookTrigger.TEST.eventName, OffsetDateTime.now(), false), null, 2_000)

    /** The Test button: sends right now, without WorkManager, and returns what came back. */
    suspend fun test(hook: Webhook): TestReport {
        val rendered = render(hook, WebhookTrigger.TEST)
        val outcome = kotlinx.coroutines.withContext(Dispatchers.IO) {
            WebhookSender.send(hook, if (hook.method.equals("GET", true)) "" else rendered.text)
        }
        if (rendered.failed.isNotEmpty()) {
            WebhookDiagnostics.logWarn("Variables failed: ${rendered.failed.joinToString()}")
        }
        return TestReport(outcome, rendered, runCatching { api.isConnected() }.getOrDefault(false))
    }

    data class TestReport(
        val outcome: WebhookSender.Outcome,
        val rendered: TemplateRenderer.Rendered,
        val watchConnected: Boolean,
    )
}
