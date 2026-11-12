package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import kotlin.collections.plus

/**
 * Maps the API's per-connection button flags to a [org.avmedia.gshockGoogleSync.session.ConnectionButton]. This is the only place in the
 * app that reads those flags: everything else asks the [Session].
 *
 * The flags are valid once the watch has reported its button, i.e. from the "ButtonPressedInfoReceived"
 * event on, so this must only be called when a connection is being initialized.
 */
internal fun ConnectionGateway.currentConnectionButton(): ConnectionButton = when {
    isActionButtonPressed() -> ConnectionButton.FROM_ACTION
    isAutoTimeStarted() -> ConnectionButton.AUTOMATIC_SYNC
    isFindPhoneButtonPressed() -> ConnectionButton.FIND
    isAlwaysConnectedConnectionPressed() -> ConnectionButton.ALWAYS_CONNECTED
    else -> ConnectionButton.NORMAL
}

/**
 * Connection-scoped Session object managing a single active watch connection lifetime.
 * Owns its own [CoroutineScope], connection state, watch snapshot, and component pending save flags.
 *
 * A Session is single-use: once [close]d it never becomes active again. All mutators are no-ops on
 * a closed session, so late callbacks from an old connection cannot resurrect it or leave stale
 * pending-save marks behind. A new connection gets a new Session (see [SessionManager]).
 */
class Session(
    val id: String,
    private val gateway: ConnectionGateway,
    device: BluetoothDevice? = null,
    initialWatch: WatchSnapshot = WatchSnapshot()
) {
    /** The connected device. May be attached after creation, once the library reports it. */
    @Volatile
    var device: BluetoothDevice? = device
        private set

    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(
        SessionState(
            phase = SessionPhase.Connecting,
            watch = initialWatch
        )
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** True until [close] is called. */
    val isActive: Boolean get() = _state.value.phase !is SessionPhase.Closed

    private val hookLock = Any()
    private val closeHooks = mutableListOf<() -> Unit>()
    private var hooksRan = false

    /** The button that started this connection; null until the session is Ready. */
    val button: ConnectionButton? get() = _state.value.button

    /**
     * Moves the session from [SessionPhase.Connecting] to [SessionPhase.Ready] and fixes its
     * [button] for the rest of the session. Returns false (and changes nothing) if the session is
     * already Ready or Closed, so repeated "initialized" events cannot change the button.
     *
     * @param button overrides the button read from the gateway (for tests).
     * @param entryReason defaults to [org.avmedia.gshockGoogleSync.session.EntryReason.Companion.forButton] of the button.
     */
    fun setReady(
        entryReason: EntryReason? = null,
        button: ConnectionButton? = null
    ): Boolean {
        val resolvedButton = button ?: gateway.currentConnectionButton()
        val resolvedReason = entryReason ?: EntryReason.forButton(resolvedButton)

        var transitioned = false
        _state.update {
            if (it.phase is SessionPhase.Connecting) {
                transitioned = true
                it.copy(phase = SessionPhase.Ready(resolvedReason), button = resolvedButton)
            } else {
                transitioned = false
                it
            }
        }
        return transitioned
    }

    fun updateWatch(transform: (WatchSnapshot) -> WatchSnapshot) {
        updateIfActive { it.copy(watch = transform(it.watch)) }
    }

    /** Records the connected device, used to tear the connection down and to match disconnects. */
    fun attachDevice(device: BluetoothDevice) {
        if (isActive) this.device = device
    }

    /** The user moved on from the entry screen (e.g. unlocked the cover) to the main navigation. */
    fun enterMainNavigation() {
        updateIfActive {
            if (it.phase is SessionPhase.Ready) it.copy(phase = SessionPhase.Ready(EntryReason.NORMAL))
            else it
        }
    }

    /** The watch asked the app to run its actions ("RunActions"). Only meaningful while Ready. */
    fun beginRunActions() {
        updateIfActive {
            if (it.phase is SessionPhase.Ready) it.copy(isRunningActions = true) else it
        }
    }

    fun endRunActions() {
        updateIfActive { it.copy(isRunningActions = false) }
    }

    fun markSetupComplete() {
        updateIfActive { it.copy(isSetupComplete = true) }
    }

    fun markPendingSave(component: ComponentId) {
        updateIfActive { it.copy(pendingSaves = it.pendingSaves + component) }
    }

    fun clearPendingSave(component: ComponentId) {
        updateIfActive { it.copy(pendingSaves = it.pendingSaves - component) }
    }

    fun isPendingSave(component: ComponentId): Boolean =
        _state.value.isPendingSave(component)

    /**
     * Registers [hook] to run once when the session closes, and returns a handle whose `close()`
     * unregisters it (idempotent; a no-op once the hook has run). If the session has already
     * closed, the hook runs immediately and the returned handle does nothing.
     */
    fun registerCloseHook(hook: () -> Unit): AutoCloseable {
        val runNow = synchronized(hookLock) {
            if (hooksRan) true else {
                closeHooks.add(hook)
                false
            }
        }
        if (runNow) {
            runHook(hook)
            return AutoCloseable { }
        }
        return AutoCloseable {
            synchronized(hookLock) { closeHooks.remove(hook) }
        }
    }

    /**
     * Closes the session. Idempotent: only the first call has any effect, so the first (usually
     * most informative) reason wins.
     *
     * @param deviceHint device to tear down when the session was created without one, e.g. the
     * payload of the "Disconnect" event.
     */
    fun close(
        reason: String = "Disconnected",
        deviceHint: BluetoothDevice? = null,
        isError: Boolean = false
    ) {
        var alreadyClosed = false
        _state.update {
            if (it.phase is SessionPhase.Closed) {
                alreadyClosed = true
                it
            } else {
                alreadyClosed = false
                it.copy(
                    phase = SessionPhase.Closed(reason, isError),
                    pendingSaves = emptySet(),
                    isRunningActions = false
                )
            }
        }
        if (alreadyClosed) return

        Timber.i("Closing Session $id: $reason")

        val hooks = synchronized(hookLock) {
            hooksRan = true
            closeHooks.toList().also { closeHooks.clear() }
        }
        hooks.forEach { runHook(it) }

        if (isError) {
            // An error close has no device payload: release the connection itself.
            runCatching { gateway.disconnect() }
                .onFailure { e -> Timber.e(e, "Error disconnecting GShockAPI in Session") }
        }
        (device ?: deviceHint)?.let {
            runCatching {
                gateway.teardownConnection(it)
            }.onFailure { e ->
                Timber.e(e, "Error tearing down GShockAPI connection in Session")
            }
        }

        sessionScope.cancel("Session closed: $reason")
    }

    private fun runHook(hook: () -> Unit) {
        runCatching { hook() }
            .onFailure { e -> Timber.e(e, "Error executing session close hook") }
    }

    /** Applies [transform] unless the session is closed. Returns whether it was applied. */
    private fun updateIfActive(transform: (SessionState) -> SessionState): Boolean {
        var applied = false
        _state.update {
            if (it.phase is SessionPhase.Closed) {
                applied = false
                it
            } else {
                applied = true
                transform(it)
            }
        }
        return applied
    }
}