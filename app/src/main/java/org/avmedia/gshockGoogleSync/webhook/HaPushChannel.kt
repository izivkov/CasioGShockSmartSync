package org.avmedia.gshockGoogleSync.webhook

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.avmedia.gshockapi.AppNotification
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Keeps one websocket to Home Assistant open (mobile_app local push) and hands each message to [deliver].
 * Quiet by design: every failure only logs and reconnects with a growing wait. The caller cancels [run]
 * when the watch disconnects. Never logs the URL, the token or the message text.
 */
class HaPushChannel(
    private val store: HaStore,
    private val deliver: (AppNotification) -> Unit,
    private val enabled: () -> Boolean = { true },
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    }

    suspend fun run(deviceId: String) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val reached = runCatching { if (enabled()) connectOnce(deviceId) else false }.getOrDefault(false)
            attempt = if (reached) 0 else attempt + 1
            delay(HaPush.backoffMs(attempt))
        }
    }

    /** One connection until it ends. True when the subscription was confirmed by HA. */
    private suspend fun connectOnce(deviceId: String): Boolean {
        val cfg = store.load() ?: return false
        val reg = store.loadRegistration(deviceId) ?: return false
        // The registration that carries the push flag is saved by the next sensor update; wait for it.
        if (!reg.registration.contains("push_websocket_channel")) return false
        val url = HaPush.wsUrl(cfg.baseUrl) ?: return false
        var subscribed = false
        suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
            var nextId = 2
            val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    when (val e = HaPush.parse(text)) {
                        HaPushEvent.AuthRequired -> webSocket.send(HaPush.auth(cfg.token))
                        HaPushEvent.AuthOk -> webSocket.send(HaPush.subscribe(1, reg.webhookId))
                        HaPushEvent.AuthInvalid -> webSocket.close(1000, null)
                        is HaPushEvent.Push -> {
                            subscribed = true
                            runCatching { deliver(HaPush.toNotification(e, now())) }.onFailure { Timber.w("HA push: not delivered") }
                            e.confirmId?.let { webSocket.send(HaPush.confirm(nextId++, reg.webhookId, it)) }
                        }
                        HaPushEvent.Ignored -> if (text.contains("\"result\"") && text.contains("\"success\":true")) subscribed = true
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (cont.isActive) cont.resume(Unit) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Timber.d("HA push: connection ended")
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
        return subscribed
    }

    private fun now(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
}
