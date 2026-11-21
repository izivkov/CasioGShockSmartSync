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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val registry: VariableRegistry = WatchVariables.registry(context, api, features)

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
        val ctx = VariableContext(trigger.eventName, OffsetDateTime.now(), api.isConnected())
        return registry.render(hook.bodyTemplate, hook.isJson, ctx)
    }

    /** The Test button: sends right now, without WorkManager, and returns what came back. */
    suspend fun test(hook: Webhook): TestReport {
        val rendered = render(hook, WebhookTrigger.TEST)
        val outcome = kotlinx.coroutines.withContext(Dispatchers.IO) {
            WebhookSender.send(hook, if (hook.method.equals("GET", true)) "" else rendered.text)
        }
        return TestReport(outcome, rendered, api.isConnected())
    }

    data class TestReport(
        val outcome: WebhookSender.Outcome,
        val rendered: TemplateRenderer.Rendered,
        val watchConnected: Boolean,
    )
}
