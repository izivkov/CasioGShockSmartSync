package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level Singleton manager providing the single source of truth for watch connection sessions.
 *
 * Invariants:
 * - Every connection gets its own [Session]; a closed session is never reused.
 * - [currentSession] keeps pointing at the last session after it closes, so observers can read the
 *   [SessionPhase.Closed] reason. It is replaced on the next connection.
 * - [sessionState] is derived from [currentSession], never copied by hand.
 */
@Singleton
class SessionManager @Inject constructor(
    private val repository: GShockRepository
) {
    private val lock = Any()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionState: StateFlow<SessionState> = _currentSession
        .flatMapLatest { session -> session?.state ?: flowOf(SessionState()) }
        .stateIn(managerScope, SharingStarted.Eagerly, SessionState())

    /**
     * Starts a new session in [SessionPhase.Connecting], closing the previous one if it is still
     * active.
     */
    fun createSession(device: BluetoothDevice? = null): Session = synchronized(lock) {
        val sessionId = UUID.randomUUID().toString()
        Timber.i("Creating new watch Session: $sessionId")

        _currentSession.value?.close("New session started")

        Session(
            id = sessionId,
            repository = repository,
            device = device
        ).also { _currentSession.value = it }
    }

    /**
     * The watch finished initializing: the session becomes Ready and its [ConnectionButton] is
     * fixed. Reuses the current session only if it is still active; otherwise starts a fresh one.
     * Idempotent, so every subscriber of "WatchInitializationCompleted" may call it and get the
     * same session back, whatever the order they run in.
     *
     * Assumes the previous connection's "Disconnect" was processed before the next connection
     * initializes, as the rest of the app already does.
     *
     * @param entryReason defaults to [EntryReason.forButton] of the connection button.
     */
    fun onConnected(
        entryReason: EntryReason? = null,
        device: BluetoothDevice? = null
    ): Session = synchronized(lock) {
        val current = _currentSession.value
        val session = if (current != null && current.isActive) current else createSession(device)
        session.setReady(entryReason)
        session
    }

    /**
     * The watch disconnected or the connection failed. Closes the current session. The first
     * reason wins: later events for an already-closed session are ignored. If no session exists
     * yet, a session is created and closed so the failure reason is still observable.
     */
    fun onDisconnected(device: BluetoothDevice? = null, reason: String = "Watch disconnected") {
        synchronized(lock) {
            val current = _currentSession.value

            if (current == null) {
                createSession(device).close(reason, device)
                return
            }
            if (!current.isActive) {
                Timber.d("Ignoring '$reason': Session ${current.id} is already closed")
                return
            }
            if (isForDifferentDevice(current, device)) {
                Timber.w("Ignoring '$reason' for ${device?.address}: current session is for ${current.device?.address}")
                return
            }
            current.close(reason, device)
        }
    }

    /** The button that started the current connection, or null before the first connection is Ready. */
    val connectionButton: ConnectionButton? get() = _currentSession.value?.button

    fun markPendingSave(component: ComponentId) {
        _currentSession.value?.markPendingSave(component)
    }

    fun clearPendingSave(component: ComponentId) {
        _currentSession.value?.clearPendingSave(component)
    }

    fun isPendingSave(component: ComponentId): Boolean =
        _currentSession.value?.isPendingSave(component) ?: false

    fun isPendingSaveFlow(component: ComponentId) =
        sessionState.map { it.isPendingSave(component) }.distinctUntilChanged()

    private fun isForDifferentDevice(session: Session, device: BluetoothDevice?): Boolean {
        val sessionDevice = session.device ?: return false
        return device != null && sessionDevice.address != device.address
    }
}
