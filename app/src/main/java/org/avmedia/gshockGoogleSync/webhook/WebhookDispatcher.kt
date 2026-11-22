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

    fun fire(trigger: WebhookTrigger) {
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

    /** Renders [hook] with the current values. [hook] may be an unsaved edit (Test button). */
    suspend fun render(hook: Webhook, trigger: WebhookTrigger): TemplateRenderer.Rendered {
        val ctx = VariableContext(trigger.eventName, OffsetDateTime.now(), runCatching { api.isConnected() }.getOrDefault(false))
        return registry.render(hook.bodyTemplate, hook.isJson, ctx)
    }

    /**
     * Posts a probe result (already JSON, event "probe") to every enabled webhook, whatever its
     * triggers. The body replaces the template; GET webhooks have no body and are skipped.
     * Returns how many webhooks it was queued for.
     */
    fun sendProbe(json: String): Int {
        var queued = 0
        WebhookStorage.loadHooks(context)
            .filter { WebhookPolicy.shouldFire(it, WebhookTrigger.PROBE, null, System.currentTimeMillis()) }
            .filterNot { it.method.equals("GET", ignoreCase = true) }
            .forEach { hook ->
                WebhookStorage.putPending(context, hook.id, json)
                WebhookWorker.enqueue(context, hook.id)
                queued++
            }
        return queued
    }

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
