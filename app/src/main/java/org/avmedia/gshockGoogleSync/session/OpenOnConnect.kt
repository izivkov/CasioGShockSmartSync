package org.avmedia.gshockGoogleSync.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * When a connection moves the app from the watch list to the watch screens: only for a connection the person
 * caused ([ConnectionCause.USER]: the watch's connect button) or the watch they tapped and wait for ([waiting], up to
 * [WAIT_MS]). The watch's own reconnect (kind 13/14) and a background read never switch screens.
 */
object OpenOnConnect {
    const val WAIT_MS = 2 * 60_000L

    private val _waiting = MutableStateFlow<String?>(null)
    @Volatile private var expiry: Job? = null

    /** Address of the watch the person is waiting for after tapping its card, or null. */
    val waiting: StateFlow<String?> = _waiting

    /** The person tapped [address] while it is not connected: wait for it up to [waitMs]. */
    fun waitFor(address: String, scope: CoroutineScope, waitMs: Long = WAIT_MS) {
        expiry?.cancel()
        _waiting.value = address
        expiry = scope.launch {
            delay(waitMs)
            if (sameAddress(_waiting.value, address)) _waiting.value = null
        }
    }

    fun cancelWaiting() {
        expiry?.cancel()
        _waiting.value = null
    }

    /**
     * True when the watch list must give way to the watch screens: a connection on the list ([EntryReason.BACKGROUND])
     * while the app is in front, caused by the person or of the watch they wait for.
     */
    fun shouldOpen(session: SessionState, inFront: Boolean, onFirstPage: Boolean, waitingFor: String?): Boolean {
        val ready = (session.phase as? SessionPhase.Ready)?.entryReason == EntryReason.BACKGROUND
        if (!inFront || !onFirstPage || !ready) return false
        return session.cause == ConnectionCause.USER || sameAddress(waitingFor, session.watch.address)
    }

    fun sameAddress(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && a.equals(b, ignoreCase = true)

    /** Called when the latch fired: the tap request is used up. */
    fun consumed(address: String) {
        if (sameAddress(_waiting.value, address)) cancelWaiting()
    }
}
