package org.avmedia.gshockGoogleSync.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage

/**
 * "Keep connected in background", for one watch at most (the holder; stored as one address, so two watches can
 * never both have it).
 * - On: the watch's own reconnects are admitted (also after an app restart), no disconnect on inactivity, the link
 *   waits across drops and the periodic LifeLog read keeps running.
 * - Off, as upstream: an always-connected watch connects only on request (a tap on its card) or in a short
 *   background window; the inactivity timeout disconnects it and a dropped link ends the session.
 */
object KeepConnected {
    const val KEY = "KeepConnectedAddress"
    private const val KEY_LAST_ADDRESS = "LastDeviceAddress"
    private const val KEY_LAST_NAME = "LastDeviceName"

    private val _holder = MutableStateFlow<String?>(null)

    /** The address of the watch that has it, or null. */
    val holder: StateFlow<String?> = _holder

    fun holds(address: String?): Boolean = OpenOnConnect.sameAddress(_holder.value, address)

    /**
     * Reads the holder (app start). Before the first choice was stored, the most recently connected always-connected
     * watch gets it ([migrationChoice]); with no such watch known yet nothing is stored and the next start asks again.
     */
    fun load(context: Context) {
        val stored = LocalDataStorage.get(context, KEY)
        if (stored != null) {
            _holder.value = stored.ifBlank { null }
            return
        }
        val last = LocalDataStorage.get(context, KEY_LAST_ADDRESS, "").orEmpty()
        val registeredIds = runCatching {
            org.avmedia.gshockGoogleSync.homeassistant.AppStores.haStore(context).registeredIds().toSet()
        }.getOrDefault(emptySet())
        val registered = { address: String -> org.avmedia.gshockGoogleSync.watch.WatchKeys.of(address) in registeredIds }
        val choice = migrationChoice(last, LocalDataStorage.getDeviceAddresses(context), registered) { address ->
            LocalDataStorage.getDeviceName(context, address)?.takeIf { it.isNotBlank() }
                ?: if (address.equals(last, ignoreCase = true)) LocalDataStorage.get(context, KEY_LAST_NAME, "").orEmpty() else ""
        } ?: return
        _holder.value = choice.uppercase()
        LocalDataStorage.put(context, KEY, choice.uppercase())
    }

    /**
     * First start: among the always-connected watches (never an OCW) the one with a Home Assistant registration, the
     * most recently connected first; else the most recently connected one. The others get off.
     */
    fun migrationChoice(
        lastAddress: String,
        known: List<String>,
        registered: (String) -> Boolean = { false },
        nameOf: (String) -> String,
    ): String? {
        val candidates = known.filter { WatchModelOverride.isAlwaysConnected(nameOf(it)) }
        val inHa = candidates.filter(registered)
        val last = { list: List<String> -> list.firstOrNull { it.equals(lastAddress, ignoreCase = true) } }
        return last(inHa) ?: inHa.lastOrNull() ?: last(candidates) ?: candidates.firstOrNull()
    }

    /** On for [address] (off for the watch that had it) or off. Returns the other watch that lost it, if any. */
    suspend fun set(context: Context, address: String, on: Boolean): String? {
        val previous = _holder.value
        val next = when {
            on -> address.uppercase()
            holds(address) -> null
            else -> previous
        }
        _holder.value = next
        LocalDataStorage.putAndWait(context, KEY, next.orEmpty())
        return previous?.takeIf { on && !OpenOnConnect.sameAddress(it, address) }
    }

    /** The watch is forgotten: it no longer has it. */
    suspend fun forget(context: Context, address: String) {
        if (holds(address)) set(context, address, false)
    }

    /**
     * Whether a watch that appeared may connect. Watches that connect on their button press always may (upstream);
     * an always-connected watch only with Keep connected, in a background window, or while the person waits for it
     * after tapping its card.
     */
    fun admits(name: String, address: String, background: Boolean, waitingFor: String?): Boolean {
        if (OpenOnConnect.sameAddress(waitingFor, address)) return true
        // The person chose another watch: the holder's own reconnects (and its background windows) wait.
        if (holds(address) && _pausedFor.value != null && !OpenOnConnect.sameAddress(_pausedFor.value, address)) return false
        return !WatchModelOverride.isAlwaysConnected(name) || holds(address) || background
    }

    private val _pausedFor = MutableStateFlow<String?>(null)

    /** The other watch the person chose while the holder keeps connected; null when the holder may reconnect. */
    val pausedFor: StateFlow<String?> = _pausedFor

    /** The person tapped [address]: another watch than the holder pauses the holder's reconnects; the holder resumes them. */
    fun chosen(address: String) {
        _pausedFor.value = if (holds(address)) null else address.uppercase()
    }

    /**
     * Follows the session and the wait: once the chosen watch has no session (it disconnected, the inactivity timeout
     * ended it) and nobody waits for it (Cancel, two minutes ran out), the holder may reconnect by itself again.
     */
    fun updatePause(session: SessionState, waitingFor: String?) {
        val chosen = _pausedFor.value ?: return
        val active = (session.phase is SessionPhase.Ready || session.phase is SessionPhase.Connecting) &&
            OpenOnConnect.sameAddress(session.watch.address, chosen)
        if (!active && !OpenOnConnect.sameAddress(waitingFor, chosen)) _pausedFor.value = null
    }

    internal fun resetForTest(holder: String? = null) {
        _holder.value = holder
        _pausedFor.value = null
    }
}
