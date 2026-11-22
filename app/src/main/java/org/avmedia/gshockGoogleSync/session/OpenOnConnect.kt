package org.avmedia.gshockGoogleSync.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage

/**
 * Whether an always-connected watch that connects moves the app from the first page to the main
 * screen. A switch per watch address ([isOn], default off), plus a tap on the watch name that opens
 * the main screen now or at the watch's next connection ([waiting]).
 */
object OpenOnConnect {
    private val _waiting = MutableStateFlow<String?>(null)

    /** Address of the watch the person is waiting for after tapping its name, or null. */
    val waiting: StateFlow<String?> = _waiting

    fun key(address: String) = "OpenOnConnect_${address.uppercase()}"

    fun isOn(context: Context, address: String): Boolean =
        address.isNotBlank() && LocalDataStorage.get(context, key(address), "false") == "true"

    suspend fun set(context: Context, address: String, on: Boolean) =
        LocalDataStorage.putAndWait(context, key(address), on.toString())

    fun waitFor(address: String) { _waiting.value = address }
    fun cancelWaiting() { _waiting.value = null }

    /**
     * True when the first page must give way to the main screen: an always-connected watch is
     * connected in the background while the app is in front on the first page, and the person asked
     * for it (switch on for this watch, or tapped its name).
     */
    fun shouldOpen(
        session: SessionState,
        inFront: Boolean,
        onFirstPage: Boolean,
        switchOn: Boolean,
        waitingFor: String?
    ): Boolean {
        val ready = (session.phase as? SessionPhase.Ready)?.entryReason == EntryReason.BACKGROUND
        if (!inFront || !onFirstPage || !ready) return false
        return true
    }

    fun sameAddress(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && a.equals(b, ignoreCase = true)

    /** Called when the latch fired: the tap request is used up. */
    fun consumed(address: String) {
        if (sameAddress(_waiting.value, address)) _waiting.value = null
    }
}
