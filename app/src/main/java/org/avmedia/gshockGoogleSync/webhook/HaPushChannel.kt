package org.avmedia.gshockGoogleSync.webhook

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.log.LogSource
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * One websocket to Home Assistant (mobile_app local push); each message goes to [deliver]. [HaPushLoop] keeps it up:
 * reconnect at once, then with a growing wait, and at once on [wake]. Never logs the URL, the token or the message text.
 */
class HaPushChannel(
    private val store: HaStore,
    private val deliver: (AppNotification) -> Unit,
    private val enabled: () -> Boolean = { true },
    private val catchUp: (deviceId: String, downSince: Long?, upAt: Long) -> Unit = { _, _, _ -> },
    private val log: (String) -> Unit = { AppLog.log(LogSource.App, it) },
) {
    private val router = HaPushRouter(deliver, now = ::now)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    }

    @Volatile private var loop: HaPushLoop? = null

    /** Cuts the wait before the next try; with [drop] a live connection is ended too (the network changed). */
    fun wake(drop: Boolean) { loop?.wake(drop) }

    suspend fun run(deviceId: () -> String) {
        log("Home Assistant: push loop started")
        val l = HaPushLoop(
            connect = { up -> if (enabled()) connectOnce(deviceId(), up).also(::noteReason) else "off".also(::noteReason) },
            now = System::currentTimeMillis,
            log = log,
            onState = { HaPushState.set(it); HaPushRouter.updateSensorsHook?.invoke() },
            onUp = { since, at -> runCatching { catchUp(deviceId(), since, at) }.onFailure { Timber.w("HA push: catch-up failed") } },
        )
        loop = l
        try { l.run() } finally {
            loop = null
            // Stopped (service gone, owners released): the state is off and HA is told, as far as the process still can.
            HaPushState.set(false)
            runCatching { HaPushRouter.updateSensorsHook?.invoke() }
        }
    }

    private var lastNote: String? = null

    /** Why the socket is not up, once per change of reason (not per retry): an empty log made a dead push invisible (2026-11-23). */
    private fun noteReason(reason: String) {
        if (reason == lastNote) return
        lastNote = reason
        if (reason in NOT_UP_REASONS) log("Home Assistant: push not connected ($reason)")
    }

    /** One connection until it ends; [up] is called when HA confirmed the subscription. Returns why it ended. */
    private suspend fun connectOnce(deviceId: String, up: () -> Unit): String {
        val cfg = store.load() ?: return "not set up"
        val reg = store.loadRegistration(deviceId) ?: return "not registered"
        // The registration that carries the push flag is saved by the next sensor update; wait for it.
        if (!reg.registration.contains("push_websocket_channel")) return "push not registered yet"
        val url = HaPush.wsUrl(cfg.baseUrl) ?: return "bad URL"
        var reason = "closed"
        suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
            var nextId = 2
            val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    when (val e = HaPush.parse(text)) {
                        HaPushEvent.AuthRequired -> webSocket.send(HaPush.auth(cfg.token))
                        HaPushEvent.AuthOk -> webSocket.send(HaPush.subscribe(1, reg.webhookId))
                        HaPushEvent.AuthInvalid -> { reason = "token refused"; webSocket.close(1000, null) }
                        is HaPushEvent.Push -> {
                            up()
                            runCatching { router.route(e) }.onFailure { Timber.w("HA push: not delivered") }
                            e.confirmId?.let { webSocket.send(HaPush.confirm(nextId++, reg.webhookId, it)) }
                        }
                        HaPushEvent.Ignored -> if (text.contains("\"result\"") && text.contains("\"success\":true")) up()
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason2: String) {
                    if (reason == "closed") reason = "closed $code"
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    reason = t.javaClass.simpleName
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
        return reason
    }

    private companion object {
        val NOT_UP_REASONS = setOf("off", "not set up", "not registered", "push not registered yet", "bad URL", "token refused")
    }

    private fun now(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
}
