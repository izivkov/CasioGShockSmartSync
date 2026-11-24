package org.avmedia.gshockGoogleSync.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The watch list (pair, choose, remove) opened from the connected screens. It is app-level, like the Home
 * Assistant screen: the session may reconnect, drop or close while it is open, and the list stays until the
 * person leaves it. While it is open, a watch that appears connects only when the list allows it ([admits]):
 * nothing connects during a pairing, and after a choice only the chosen watch does.
 */
object WatchList {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open

    @Volatile private var chosen: String? = null
    @Volatile private var pairing = false

    fun show() { _open.value = true }

    fun hide() {
        _open.value = false
        chosen = null
        pairing = false
    }

    fun pairingStarted() { if (_open.value) pairing = true }

    fun pairingEnded() { pairing = false }

    /** The person chose (or just paired) [address]: from now on only that watch may connect while the list is open. */
    fun choose(address: String) {
        pairing = false
        if (_open.value) chosen = address
    }

    /** Whether a watch that appeared may connect now. Always true while the list is closed. */
    fun admits(address: String): Boolean {
        if (!_open.value) return true
        if (pairing) return false
        val only = chosen ?: return true
        return OpenOnConnect.sameAddress(only, address)
    }
}

/**
 * Switching to another watch from the watch list. The library holds one connection at a time, so the watch now
 * connected (if it is another one) is disconnected once, then the chosen one is connected.
 */
class WatchSwitch(
    private val connectedAddress: () -> String?,
    private val disconnectCurrent: () -> Unit,
    private val connect: (String) -> Unit,
) {
    fun choose(address: String) {
        WatchList.choose(address)
        val current = connectedAddress()?.takeIf { it.isNotBlank() }
        if (current != null && OpenOnConnect.sameAddress(current, address)) return
        if (current != null) disconnectCurrent()
        connect(address)
    }

    /** A pairing starts: the connected watch is let go first, so it cannot take the link over the new one. */
    fun startPairing() {
        WatchList.pairingStarted()
        if (connectedAddress()?.isNotBlank() == true) disconnectCurrent()
    }
}
