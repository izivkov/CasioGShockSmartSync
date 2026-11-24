package org.avmedia.gshockGoogleSync.homeassistant

/**
 * The messages of one Home Assistant push socket, without the socket (unit tested). [send] writes to the socket,
 * [up] tells the loop the push channel is up, [end] closes the socket with a reason, [route] handles a push.
 *
 * "Up" is HA's success answer to our `mobile_app/push_notification_channel`, never "socket open": HA ends a channel
 * without a word on the socket (a push not confirmed within 10 s, a second subscription for the mobile_app webhook), and a
 * confirm then gets `not_found`. That ends the socket, so the loop reports the outage and subscribes again at once.
 */
class HaPushSession(
    private val token: String,
    /** The connected (or last connected) watch's webhook: the channel is up once HA confirmed this one. */
    private val webhookId: String,
    private val send: (String) -> Boolean,
    private val up: () -> Unit,
    private val end: (reason: String) -> Unit,
    private val route: (HaPushEvent.Push) -> Unit,
    private val log: (String) -> Unit,
    /** The other registered watches' webhooks: their messages are received too, so they can be dropped with a log line. */
    private val others: List<String> = emptyList(),
    private val routeOther: (HaPushEvent.Push, webhookId: String) -> Unit = { _, _ -> },
) {
    private var nextId = 1
    private var subscriptionId = -1
    private val otherSubscriptions = HashMap<Int, String>()
    private val confirms = HashSet<Int>()

    fun onText(text: String) {
        when (val e = HaPush.parse(text)) {
            HaPushEvent.AuthRequired -> send(HaPush.auth(token))
            HaPushEvent.AuthOk -> {
                subscriptionId = nextId++
                send(HaPush.subscribe(subscriptionId, webhookId))
                for (hook in others.filter { false }) {
                    val id = nextId++
                    otherSubscriptions[id] = hook
                    send(HaPush.subscribe(id, hook))
                }
            }
            HaPushEvent.AuthInvalid -> end("token refused")
            is HaPushEvent.Push -> {
                val hook = when (e.subscriptionId) {
                    null, subscriptionId -> webhookId
                    else -> otherSubscriptions[e.subscriptionId] ?: return
                }
                // Confirm first: HA drops the channel when the confirm is 10 s late, and handling may write to the watch.
                e.confirmId?.let {
                    val id = nextId++
                    confirms += id
                    if (!send(HaPush.confirm(id, hook, it))) end("confirm not sent")
                }
                if (hook == webhookId) route(e) else routeOther(e, hook)
            }
            is HaPushEvent.Result -> when {
                e.id == subscriptionId && e.success -> { log("Home Assistant: push subscribed"); up() }
                e.id == subscriptionId -> end("subscribe refused (${e.code})")
                // Another watch's channel refused: that watch only misses its messages; the connected one stays up.
                e.id in otherSubscriptions && !e.success -> { otherSubscriptions.remove(e.id); log("Home Assistant: push for another watch refused (${e.code})") }
                e.id in otherSubscriptions -> {}
                confirms.remove(e.id) && !e.success -> end("subscription lost (confirm ${e.code})")
            }
            HaPushEvent.Ignored -> {}
        }
    }
}
