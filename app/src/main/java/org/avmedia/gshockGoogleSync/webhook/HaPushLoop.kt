package org.avmedia.gshockGoogleSync.webhook

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the push connection up. [connect] runs one connection until it ends and returns why; it calls its `up`
 * argument once the subscription is confirmed. After a drop it tries again at once, then waits [delayFor]
 * (1, 2, 5, 10, 30, 60 s). One log line when an outage starts and one when it ends, none per retry.
 * [wake] cuts the wait (the watch connected); with `drop` it also ends a live connection (the network changed).
 */
class HaPushLoop(
    private val connect: suspend (up: () -> Unit) -> String,
    private val now: () -> Long,
    private val log: (String) -> Unit,
    private val onState: (Boolean) -> Unit = {},
    /** Called when the socket is up. [downSince] is when the last outage began (null = first connection). */
    private val onUp: (downSince: Long?, upAt: Long) -> Unit = { _, _ -> },
    private val delayFor: (Int) -> Long = HaPush::backoffMs,
) {
    private val wakes = Channel<Unit>(Channel.CONFLATED)
    private val dropRequested = AtomicBoolean(false)

    fun wake(drop: Boolean) {
        if (drop) dropRequested.set(true)
        wakes.trySend(Unit)
    }

    suspend fun run() {
        var attempt = 0
        var downSince: Long? = null
        while (currentCoroutineContext().isActive) {
            while (wakes.tryReceive().isSuccess) { /* a wake from before this try is spent */ }
            dropRequested.set(false)
            val up = AtomicBoolean(false)
            val reason = race {
                connect {
                    if (up.compareAndSet(false, true)) {
                        val at = now()
                        val since = downSince
                        if (since != null) log("Home Assistant: push connection back after ${(at - since) / 1000} s")
                        downSince = null
                        onState(true)
                        onUp(since, at)
                    }
                }
            }
            if (up.get()) {
                downSince = now()
                attempt = 0
                // A failing log or state callback must never end the loop: a dead loop is a socket that never comes back.
                try {
                    log("Home Assistant: push connection lost ($reason)")
                    onState(false)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    runCatching { log("Home Assistant: push state not reported (${t.javaClass.simpleName})") }
                }
            } else {
                attempt++
            }
            val woken = withTimeoutOrNull(delayFor(attempt)) { wakes.receive() }
            if (woken != null) attempt = 0
        }
    }

    private suspend fun race(block: suspend () -> String): String = coroutineScope {
        val c = async {
            try { block() } catch (e: CancellationException) { throw e } catch (t: Throwable) { t.javaClass.simpleName }
        }
        val k = launch {
            while (true) {
                wakes.receive()
                if (dropRequested.getAndSet(false)) { c.cancel(CancellationException("network changed")); break }
            }
        }
        c.join()
        k.cancel()
        if (c.isCancelled) "network changed" else c.await()
    }
}
