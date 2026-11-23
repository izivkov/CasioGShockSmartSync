package org.avmedia.gshockGoogleSync.webhook

/**
 * The messages of one Home Assistant push socket, without the socket (unit tested). [send] writes to the socket,
 * [up] tells the loop the push channel is up, [end] closes the socket with a reason, [route] handles a push.
 */
class HaPushSession(
    private val token: String,
    private val webhookId: String,
    private val send: (String) -> Boolean,
    private val up: () -> Unit,
    private val end: (reason: String) -> Unit,
    private val route: (HaPushEvent.Push) -> Unit,
    private val log: (String) -> Unit,
) {
    private var nextId = 2

    fun onText(text: String) {
        when (val e = HaPush.parse(text)) {
            HaPushEvent.AuthRequired -> send(HaPush.auth(token))
            HaPushEvent.AuthOk -> send(HaPush.subscribe(1, webhookId))
            HaPushEvent.AuthInvalid -> end("token refused")
            is HaPushEvent.Push -> {
                up()
                runCatching { route(e) }
                e.confirmId?.let { send(HaPush.confirm(nextId++, webhookId, it)) }
            }
            HaPushEvent.Ignored -> if (text.contains("\"result\"") && text.contains("\"success\":true")) up()
        }
    }
}
