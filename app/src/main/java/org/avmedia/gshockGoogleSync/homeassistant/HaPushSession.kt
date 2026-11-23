package org.avmedia.gshockGoogleSync.homeassistant

/**
 * The messages of one Home Assistant push socket, without the socket (unit tested). [send] writes to the socket,
 * [up] tells the loop the push channel is up, [end] closes the socket with a reason, [route] handles a push.
 *
 * "Up" is HA's success answer to our `mobile_app/push_notification_channel`, never "socket open": HA ends a channel
 * without a word on the socket (a push not confirmed within 10 s, a second subscription for the mobile_app webhook), and a
 * confirm then gets `not_found`. That ends the socket, so the loop reports the outage and subscribes again at once
 * (2026-11-23: wrong confirm key, channel gone 10 s after the first push, sensor still on).
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
    private var nextId = 1
    private var subscriptionId = -1
    private val confirms = HashSet<Int>()

    fun onText(text: String) {
        when (val e = HaPush.parse(text)) {
            HaPushEvent.AuthRequired -> send(HaPush.auth(token))
            HaPushEvent.AuthOk -> {
                subscriptionId = nextId++
                send(HaPush.subscribe(subscriptionId, webhookId))
            }
            HaPushEvent.AuthInvalid -> end("token refused")
            is HaPushEvent.Push -> {
                if (e.subscriptionId != null && e.subscriptionId != subscriptionId) return
                // Confirm first: HA drops the channel when the confirm is 10 s late, and handling may write to the watch.
                e.confirmId?.let {
                    val id = nextId++
                    confirms += id
                    if (!send(HaPush.confirm(id, webhookId, it))) end("confirm not sent")
                }
                route(e)
            }
            is HaPushEvent.Result -> when {
                e.id == subscriptionId && e.success -> { log("Home Assistant: push subscribed"); up() }
                e.id == subscriptionId -> end("subscribe refused (${e.code})")
                confirms.remove(e.id) && !e.success -> end("subscription lost (confirm ${e.code})")
            }
            HaPushEvent.Ignored -> {}
        }
    }
}
