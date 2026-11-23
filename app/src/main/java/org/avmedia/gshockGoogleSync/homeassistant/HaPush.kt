package org.avmedia.gshockGoogleSync.homeassistant

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.NotificationType

/** What the Home Assistant websocket sent, reduced to what the push channel needs. */
sealed interface HaPushEvent {
    data object AuthRequired : HaPushEvent
    data object AuthOk : HaPushEvent
    data object AuthInvalid : HaPushEvent
    data class Push(
        val title: String?,
        val message: String,
        val confirmId: String?,
        /** The companion-app command this message carries (`command_update_sensors`, ...), null for a normal message. */
        val command: String? = null,
        /** The `data` object of the message (the arguments of a command), empty when none. */
        val data: Map<String, Any?> = emptyMap(),
        /** The id of the subscription this event belongs to. */
        val subscriptionId: Int? = null,
    ) : HaPushEvent
    /** HA's answer to our command [id]; [code] is HA's error code when not [success]. */
    data class Result(val id: Int, val success: Boolean, val code: String?) : HaPushEvent
    data object Ignored : HaPushEvent
}

/** The JSON of the mobile_app local push channel (websocket). Pure, so it is unit tested. */
object HaPush {
    /** The first field when a push has no title and no data.app. */
    const val SHORT_SOURCE = "HA"
    /** Longest app name and title field sent, in UTF-8 bytes (Gadgetbridge's Casio support cuts both at 32). */
    const val MAX_FIRST_FIELD_BYTES = 32

    /** [s] cut to at most [max] UTF-8 bytes, never inside a character. */
    fun cutUtf8(s: String, max: Int): String {
        var bytes = 0
        val out = StringBuilder()
        for (ch in s.codePoints().toArray()) {
            val n = String(Character.toChars(ch)).toByteArray(Charsets.UTF_8).size
            if (bytes + n > max) break
            bytes += n
            out.appendCodePoint(ch)
        }
        return out.toString()
    }
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
                val dataCommand = e.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("command")?.takeIf { it.isJsonPrimitive }?.asString
                if (message.isNullOrBlank()) HaPushEvent.Ignored
                // HA's key is hass_confirm_id (mobile_app push_notification.py); "confirm_id" is only the name in the answer.
                else HaPushEvent.Push(str("title"), message, str("hass_confirm_id"), commandOf(message, dataCommand), dataOf(e), o.get("id")?.asInt)
            }
            "result" -> HaPushEvent.Result(
                o.get("id")?.asInt ?: -1,
                o.get("success")?.asBoolean == true,
                o.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.get("code")?.asString,
            )
            else -> HaPushEvent.Ignored
        }
    }.getOrDefault(HaPushEvent.Ignored)

    const val COMMAND_WATCH_SETTING = "command_watch_setting"

    @Suppress("UNCHECKED_CAST")
    private fun dataOf(event: com.google.gson.JsonObject): Map<String, Any?> =
        event.get("data")?.takeIf { it.isJsonObject }?.let { runCatching { gson.fromJson(it, Map::class.java) as Map<String, Any?> }.getOrNull() } ?: emptyMap()

    const val COMMAND_UPDATE_SENSORS = "command_update_sensors"
    private val COMMAND_WORDS = setOf("request_location_update", "clear_notification")

    /** The command in a push: data.command when present, else the message when it is a command word. Null for a normal message. */
    fun commandOf(message: String, dataCommand: String?): String? {
        dataCommand?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val m = message.trim()
        return m.takeIf { it.startsWith("command_") || it in COMMAND_WORDS }
    }

    /**
     * The H5600 shows the app name field first and has little room, so it carries the content: the push title (or
     * `data.app`, a short source name, when the automation sets one; the title then goes in the title field). The
     * message is the text. No title and no `data.app`: [SHORT_SOURCE].
     */
    fun toNotification(push: HaPushEvent.Push, timestamp: String): AppNotification {
        val title = push.title?.trim()?.takeIf { it.isNotEmpty() }
        val source = (push.data["app"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val first = source ?: title ?: SHORT_SOURCE
        return AppNotification.create(
            type = NotificationType.MESSAGE,
            timestamp = timestamp,
            app = cutUtf8(first, MAX_FIRST_FIELD_BYTES),
            title = if (source != null) cutUtf8(title.orEmpty(), MAX_FIRST_FIELD_BYTES) else "",
            text = push.message,
        )
    }

    private val BACKOFF_MS = longArrayOf(0, 1_000, 2_000, 5_000, 10_000, 30_000, 60_000)

    /** Wait before reconnect try [attempt]: 0 = at once (right after a drop), then 1, 2, 5, 10, 30 s, then 60 s. */
    fun backoffMs(attempt: Int): Long = BACKOFF_MS[attempt.coerceIn(0, BACKOFF_MS.size - 1)]
}
