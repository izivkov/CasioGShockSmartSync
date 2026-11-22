package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.NotificationType

/** What the Home Assistant websocket sent, reduced to what the push channel needs. */
sealed interface HaPushEvent {
    data object AuthRequired : HaPushEvent
    data object AuthOk : HaPushEvent
    data object AuthInvalid : HaPushEvent
    data class Push(val title: String?, val message: String, val confirmId: String?) : HaPushEvent
    data object Ignored : HaPushEvent
}

/** The JSON of the mobile_app local push channel (websocket). Pure, so it is unit tested. */
object HaPush {
    const val APP_NAME = "Home Assistant"
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    /** "https://ha.example/" gives "wss://ha.example/api/websocket"; null when not an http(s) URL. */
    fun wsUrl(base: String): String? {
        val b = HaClient.normalizeBase(base) ?: return null
        return (if (b.startsWith("https://")) "wss://" + b.removePrefix("https://") else "ws://" + b.removePrefix("http://")) + "/api/websocket"
    }

    fun auth(token: String): String = gson.toJson(linkedMapOf("type" to "auth", "access_token" to token))

    /** Subscribes to the push channel of [webhookId], asking HA to send confirm ids. */
    fun subscribe(id: Int, webhookId: String): String = gson.toJson(
        linkedMapOf("id" to id, "type" to "mobile_app/push_notification_channel", "webhook_id" to webhookId, "support_confirm" to true)
    )

    fun confirm(id: Int, webhookId: String, confirmId: String): String = gson.toJson(
        linkedMapOf("id" to id, "type" to "mobile_app/push_notification_confirm", "webhook_id" to webhookId, "confirm_id" to confirmId)
    )

    fun parse(text: String): HaPushEvent = runCatching {
        val o = JsonParser.parseString(text).asJsonObject
        when (o.get("type")?.asString) {
            "auth_required" -> HaPushEvent.AuthRequired
            "auth_ok" -> HaPushEvent.AuthOk
            "auth_invalid" -> HaPushEvent.AuthInvalid
            "event" -> {
                val e = o.getAsJsonObject("event")
                fun str(k: String) = e.get(k)?.takeIf { it.isJsonPrimitive }?.asString
                val message = str("message")
                if (message.isNullOrBlank()) HaPushEvent.Ignored else HaPushEvent.Push(str("title"), message, str("confirm_id"))
            }
            else -> HaPushEvent.Ignored
        }
    }.getOrDefault(HaPushEvent.Ignored)

    fun toNotification(push: HaPushEvent.Push, timestamp: String): AppNotification =
        AppNotification.create(
            type = NotificationType.MESSAGE,
            timestamp = timestamp,
            app = APP_NAME,
            title = push.title?.takeIf { it.isNotBlank() } ?: APP_NAME,
            text = push.message,
        )

    /** Wait before reconnect try [attempt] (0 = first), growing, capped. */
    fun backoffMs(attempt: Int): Long = (2_000L shl attempt.coerceIn(0, 5)).coerceAtMost(60_000L)
}
