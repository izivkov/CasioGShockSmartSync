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
 *   [org.avmedia.gshockGoogleSync.session.SessionPhase.Closed] reason. It is replaced on the next connection.
 * - [sessionState] is derived from [currentSession], never copied by hand.
 */
@Singleton
class SessionManager(
    private val gateway: ConnectionGateway
) {
    @Inject
    constructor(repository: GShockRepository) : this(RepositoryConnectionGateway(repository))

    private val lock = Any()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    /**
     * The current session if it has not closed, else null. [currentSession] keeps pointing at the
     * last session after it closes (so its close reason stays readable); use this when you need a
     * live connection.
     */
    val activeSession: Session? get() = _currentSession.value?.takeIf { it.isActive }

    /** True while the current session is [org.avmedia.gshockGoogleSync.session.SessionPhase.Ready]: the watch is connected and initialized. */
    val isReady: Boolean get() = _currentSession.value?.state?.value?.phase is SessionPhase.Ready

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
            gateway = gateway,
            device = device
        ).also { _currentSession.value = it }
    }

    /**
     * A connection attempt started, or the BLE link came up. Makes sure there is an active session
     * in [SessionPhase.Connecting]. Idempotent: the library emits "ConnectionStarted" more than once
     * per attempt, and a session that is already active (Connecting or Ready) is kept, never
     * replaced. Only a missing or closed session is replaced.
     */
    fun onConnecting(device: BluetoothDevice? = null): Session = synchronized(lock) {
        ensureActiveSession(device)
    }

    /**
     * The watch finished initializing: the session becomes Ready and its [org.avmedia.gshockGoogleSync.session.ConnectionButton] is
     * fixed. Uses the active session (normally the one created by [onConnecting]) or starts a new
     * one. Idempotent, so every subscriber of "WatchInitializationCompleted" may call it and get the
     * same session back, whatever the order they run in.
     *
     * Assumes the previous connection's "Disconnect" was processed before the next connection
     * initializes, as the rest of the app already does.
     *
     * @param entryReason defaults to [org.avmedia.gshockGoogleSync.session.EntryReason.Companion.forButton] of the connection button.
     */
    fun onConnected(
        entryReason: EntryReason? = null,
        device: BluetoothDevice? = null
    ): Session = synchronized(lock) {
        ensureActiveSession(device).also { it.setReady(entryReason) }
    }

    /** The watch reported its name ("DeviceName"). Ignored when no session is active. */
    fun onDeviceName(name: String) {
        activeSession?.updateWatch { it.copy(name = name) }
            ?: Timber.w("Ignoring device name '$name': no active session")
    }

    /** The watch reported its address ("DeviceAddress"). Ignored when no session is active. */
    fun onDeviceAddress(address: String) {
        activeSession?.updateWatch { it.copy(address = address) }
            ?: Timber.w("Ignoring device address '$address': no active session")
    }

    /**
     * The connection setup finished ("ConnectionSetupComplete"): the link is up and the watch
     * info is available. Ignored when no session is active.
     */
    fun onSetupComplete(device: BluetoothDevice?) {
        val session = activeSession
        if (session == null) {
            Timber.w("Ignoring setup complete: no active session")
            return
        }
        device?.let(session::attachDevice)
        session.markSetupComplete()
    }

    /**
     * An API-level error ("ApiError"). Fatal only while no connection is established, i.e. while
     * connecting or with no session: then it closes the session with [reason]. Once the session is
     * Ready it is just a failed operation (an invalid time zone, a failed write), so it is logged
     * and the session stays up. If the library also disconnects, "Disconnect" closes it.
     */
    fun onApiError(reason: String) {
        synchronized(lock) {
            val phase = activeSession?.state?.value?.phase
            if (phase is SessionPhase.Ready) {
                Timber.w("Ignoring API error while the session is Ready: $reason")
                return
            }
            onDisconnected(reason = reason)
        }
    }

    /**
     * The watch disconnected or the connection failed. Closes the current session. The first
     * reason wins: later events for an already-closed session are ignored. If no session exists
     * yet, a session is created and closed so the failure reason is still observable.
     */
    fun onDisconnected(
        device: BluetoothDevice? = null,
        reason: String = "Watch disconnected",
        isError: Boolean = false
    ) {
        synchronized(lock) {
            val current = _currentSession.value

            if (current == null) {
                createSession(device).close(reason, device, isError)
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
            current.close(reason, device, isError)
        }
    }

    /**
     * An error the user should be told about. Closes the session with [reason] (blank means "use
     * the default message") and releases the connection. Components call this instead of
     * broadcasting an "Error" event.
     */
    fun onError(reason: String = "") {
        onDisconnected(reason = reason, isError = true)
    }

    /** The watch asked the app to run its actions ("RunActions"). Ignored without an active session. */
    fun onRunActionsRequested() {
        activeSession?.beginRunActions()
            ?: Timber.w("Ignoring run-actions request: no active session")
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

    private fun ensureActiveSession(device: BluetoothDevice?): Session =
        activeSession ?: createSession(device)

    private fun isForDifferentDevice(session: Session, device: BluetoothDevice?): Boolean {
        val sessionDevice = session.device ?: return false
        return device != null && sessionDevice.address != device.address
    }
}