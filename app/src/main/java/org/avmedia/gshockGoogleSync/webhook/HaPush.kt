package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockapi.AppNotification

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

    /** "https://ha.example/" gives "wss://ha.example/api/websocket"; null when not an http(s) URL. */
    fun wsUrl(base: String): String? = TODO()

    fun auth(token: String): String = TODO()

    /** Subscribes to the push channel of [webhookId], asking HA to send confirm ids. */
    fun subscribe(id: Int, webhookId: String): String = TODO()

    fun confirm(id: Int, webhookId: String, confirmId: String): String = TODO()

    fun parse(text: String): HaPushEvent = TODO()

    fun toNotification(push: HaPushEvent.Push, timestamp: String): AppNotification = TODO()

    /** Wait before reconnect try [attempt] (0 = first), growing, capped. */
    fun backoffMs(attempt: Int): Long = TODO()
}
