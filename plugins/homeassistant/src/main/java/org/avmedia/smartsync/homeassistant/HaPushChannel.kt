package org.avmedia.smartsync.homeassistant

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.avmedia.smartsync.plugin.api.WatchNotification
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * One websocket to Home Assistant (mobile_app local push); each message goes to [deliver]. [HaPushLoop] keeps it up:
 * reconnect at once, then with a growing wait, and at once on [wake]. Never logs the URL, the token or the message text.
 */
class HaPushChannel(
    private val store: HaStore,
    private val deliver: (WatchNotification) -> Unit,
    private val enabled: () -> Boolean = { true },
    private val catchUp: (deviceId: String, downSince: Long?, upAt: Long) -> Unit = { _, _, _ -> },
    private val log: (String) -> Unit = { AppLog.log(LogSource.App, it) },
    /** The connected watch's key ("" for none): a message for any other watch is dropped ([HaPushGate]). */
    private val connected: () -> String = { "" },
    /** HA asked for a sensor push, or the socket went up or down (the ha_push_connected sensor). */
    private val updateSensors: () -> Unit = {},
    /** HA's `command_watch_setting`. */
    private val watchSetting: (Map<String, Any?>) -> Unit = {},
) {
    private val router = HaPushRouter(deliver, updateSensors = updateSensors, watchSetting = watchSetting)
    private val gate = HaPushGate(store, connected, log)

    /** The watches whose channels the live socket subscribed, primary first; a change subscribes again. */
    @Volatile private var subscribed: List<String> = emptyList()
    @Volatile private var wanted: () -> List<String> = { emptyList() }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    }

    @Volatile private var loop: HaPushLoop? = null

    /**
     * Cuts the wait before the next try; with [drop] a live connection is ended too (the network changed). Another
     * connected watch, or a new registration, also subscribes again: the socket then listens for the right watches.
     */
    fun wake(drop: Boolean) {
        val changed = subscribed.isNotEmpty() && runCatching { wanted() }.getOrDefault(subscribed) != subscribed
        loop?.wake(drop || changed)
    }

    /** [deviceId] is the connected (else the last) watch; [others] are the other registered watches, also listened to. */
    suspend fun run(others: () -> List<String> = { emptyList() }, deviceId: () -> String) {
        log("Home Assistant: push loop started")
        wanted = { deviceId().let { id -> listOf(id) + pushable(id, others()) } }
        val l = HaPushLoop(
            connect = { up -> if (enabled()) connectOnce(deviceId(), others(), up).also(::noteReason) else "off".also(::noteReason) },
            now = System::currentTimeMillis,
            log = log,
            onState = { HaPushState.set(it); updateSensors() },
            // Settings from HA's helper go to the connected watch only, never to the next watch that connects.
            onUp = { since, at ->
                val id = deviceId()
                if (id.isNotBlank() && id == connected()) runCatching { catchUp(id, since, at) }.onFailure { Timber.tag(AppLog.TAG).w("HA push: catch-up failed") }
            },
        )
        loop = l
        try { l.run() } finally {
            loop = null
            // Stopped (service gone, owners released): the state is off and HA is told, as far as the process still can.
            HaPushState.set(false)
            runCatching { updateSensors() }
        }
    }

    /** The other watches whose registration has the push channel. */
    private fun pushable(deviceId: String, others: List<String>): List<String> =
        others.filter { it != deviceId }.distinct().filter { store.loadRegistration(it)?.registration?.contains("push_websocket_channel") == true }

    private var lastNote: String? = null

    /** Why the socket is not up, once per change of reason (not per retry): an empty log makes a dead push invisible. */
    private fun noteReason(reason: String) {
        if (reason == lastNote) return
        lastNote = reason
        if (reason in NOT_UP_REASONS || reason.startsWith("subscribe refused")) log("Home Assistant: push not connected ($reason)")
    }

    /** One connection until it ends; [up] is called when HA confirmed the subscription. Returns why it ended. */
    private suspend fun connectOnce(deviceId: String, others: List<String>, up: () -> Unit): String {
        val cfg = store.load() ?: return "not set up"
        val reg = store.loadRegistration(deviceId) ?: return "not registered"
        // The registration that carries the push flag is saved by the next sensor update; wait for it.
        if (!reg.registration.contains("push_websocket_channel")) return "push not registered yet"
        val url = HaPush.wsUrl(cfg.baseUrl) ?: return "bad URL"
        val otherIds = pushable(deviceId, others)
        val otherHooks = otherIds.mapNotNull { store.loadRegistration(it)?.webhookId }
        subscribed = listOf(deviceId) + otherIds
        var reason = "closed"
        suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
            var socket: WebSocket? = null
            val session = HaPushSession(
                token = cfg.token,
                webhookId = reg.webhookId,
                send = { socket?.send(it) ?: false },
                up = up,
                end = { why -> reason = why; socket?.close(1000, null) },
                route = { p -> if (gate.accept(reg.webhookId, p)) runCatching { router.route(p) }.onFailure { Timber.tag(AppLog.TAG).w("HA push: not delivered") } },
                log = log,
                others = otherHooks,
                routeOther = { p, hook -> if (gate.accept(hook, p)) runCatching { router.route(p) }.onFailure { Timber.tag(AppLog.TAG).w("HA push: not delivered") } },
            )
            val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { socket = webSocket }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    socket = webSocket
                    session.onText(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason2: String) {
                    if (reason == "closed") reason = "closed $code"
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (reason == "closed") reason = t.javaClass.simpleName
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
        subscribed = emptyList()
        return reason
    }

    private companion object {
        val NOT_UP_REASONS = setOf("off", "not set up", "not registered", "push not registered yet", "bad URL", "token refused")
    }

}
