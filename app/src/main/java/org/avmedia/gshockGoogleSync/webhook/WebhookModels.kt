package org.avmedia.gshockGoogleSync.webhook

import androidx.annotation.Keep
import java.util.UUID

/** What caused a webhook to fire. [eventName] is what the `event` variable holds. */
enum class WebhookTrigger(val eventName: String) {
    /** The watch asked the app to run its actions (button press, Phone Finder on always-connected watches). */
    WATCH_ACTION("watch_action"),

    /** After each watch connection / sync. */
    SYNC("sync"),

    /** Future: health data was downloaded from the watch. Nothing fires this yet, see [WebhookDispatcher.onHealthDataDownloaded]. */
    HEALTH_DATA("health_data"),

    /** A DW-H5600 workout was downloaded and stored. */
    WORKOUT("workout_downloaded"),

    /** The Test button. */
    TEST("test"),
}

@Keep
data class WebhookHeader(val key: String = "", val value: String = "")

@Keep
data class Webhook(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    /** POST, PUT or GET. */
    val method: String = "POST",
    val headers: List<WebhookHeader> = emptyList(),
    val contentType: String = "application/json",
    val bodyTemplate: String = "{{all}}",
    val onWatchAction: Boolean = true,
    val onSync: Boolean = false,
    val onHealthData: Boolean = false,
    val onWorkout: Boolean = false,
    /** Minimum time between two automatic sends, in minutes. 0 means no limit. */
    val minIntervalMinutes: Int = 0,
    val enabled: Boolean = true,
) {
    fun firesOn(trigger: WebhookTrigger): Boolean = when (trigger) {
        WebhookTrigger.WATCH_ACTION -> onWatchAction
        WebhookTrigger.SYNC -> onSync
        WebhookTrigger.HEALTH_DATA -> onHealthData
        WebhookTrigger.WORKOUT -> onHealthData
        WebhookTrigger.TEST -> true
    }

    val isJson: Boolean get() = TemplateRenderer.isJsonContentType(contentType)

    companion object {
        const val DEFAULT_SYNC_INTERVAL_MINUTES = 5
        val METHODS = listOf("POST", "PUT", "GET")

        /** A new webhook as the "Add" button creates it. */
        fun newDefault(): Webhook = Webhook(
            // Main use: send all data after each sync, at most every 5 minutes.
            onWatchAction = false,
            onSync = true,
            minIntervalMinutes = DEFAULT_SYNC_INTERVAL_MINUTES,
        )
    }
}

/** Last outcome of one webhook, shown on the Webhooks screen. */
@Keep
data class WebhookResult(
    val timeMillis: Long = 0L,
    /** HTTP status, or null when no response was received. */
    val status: Int? = null,
    val ok: Boolean = false,
    /** First characters of the response, or the error. */
    val message: String = "",
    val attempts: Int = 1,
    /** True while a retry is still scheduled. */
    val retrying: Boolean = false,
)

/** A template preset the editor can fill in. */
data class WebhookPreset(
    val label: String,
    val contentType: String,
    val bodyTemplate: String,
    val method: String = "POST",
    /** Triggers the preset sets; null leaves the current ones. */
    val onWatchAction: Boolean? = null,
    val onSync: Boolean? = null,
    /** Shown as the URL field's placeholder when the URL is empty. */
    val urlHint: String? = null,
)

object WebhookPresets {
    val RAW_JSON = WebhookPreset("Raw JSON", "application/json", "{{all}}")

    val TRMNL = WebhookPreset(
        "TRMNL",
        "application/json",
        """{"merge_variables": {{all}}, "merge_strategy": "deep_merge"}""",
    )

    val PLAIN_TEXT = WebhookPreset(
        "Plain text",
        "text/plain",
        "{{watch.name}}: battery {{watch.battery}}%, event {{event}} at {{timestamp}}",
    )

    val ALL = listOf(RAW_JSON, TRMNL, PLAIN_TEXT)
}
