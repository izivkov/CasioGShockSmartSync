package org.avmedia.gshockGoogleSync.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** RED CHECK stub of the opening rule; the real one in the next commit. */
object OpenOnConnect {
    const val WAIT_MS = 2 * 60_000L

    private val _waiting = MutableStateFlow<String?>(null)

    /** Address of the watch the person is waiting for after tapping its card, or null. */
    val waiting: StateFlow<String?> = _waiting

    fun waitFor(address: String, scope: CoroutineScope, waitMs: Long = WAIT_MS) { _waiting.value = address }
    fun cancelWaiting() { _waiting.value = null }

    fun shouldOpen(session: SessionState, inFront: Boolean, onFirstPage: Boolean, waitingFor: String?): Boolean {
        val ready = (session.phase as? SessionPhase.Ready)?.entryReason == EntryReason.BACKGROUND
        if (!inFront || !onFirstPage || !ready) return false
        return sameAddress(waitingFor, session.watch.address)
    }

    fun sameAddress(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && a.equals(b, ignoreCase = true)

    fun consumed(address: String) {
        if (sameAddress(_waiting.value, address)) _waiting.value = null
    }
}
