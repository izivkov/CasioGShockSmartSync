package org.avmedia.gshockGoogleSync.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import timber.log.Timber

/**
 * Connection-scoped Session object managing a single active watch connection lifetime.
 * Owns its own [CoroutineScope], connection state, watch snapshot, and component pending save flags.
 */
class Session(
    val id: String,
    private val repository: GShockRepository,
    val device: android.bluetooth.BluetoothDevice? = null,
    initialWatch: WatchSnapshot = WatchSnapshot(),
    initialTrigger: ConnectionTrigger = ConnectionTrigger.NORMAL
) {
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(
        SessionState(
            phase = SessionPhase.Connecting,
            trigger = initialTrigger,
            watch = initialWatch
        )
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val closeHooks = mutableListOf<() -> Unit>()

    fun evaluateConnectionTrigger(): ConnectionTrigger = when {
        repository.isActionButtonPressed() -> ConnectionTrigger.ACTION_BUTTON
        repository.isAutoTimeStarted() -> ConnectionTrigger.AUTO_TIME
        repository.isFindPhoneButtonPressed() -> ConnectionTrigger.FIND_PHONE
        repository.isAlwaysConnectedConnectionPressed() -> ConnectionTrigger.ALWAYS_CONNECTED
        else -> ConnectionTrigger.NORMAL
    }

    fun setReady(entryReason: EntryReason, trigger: ConnectionTrigger = evaluateConnectionTrigger()) {
        _state.update {
            it.copy(
                phase = SessionPhase.Ready(entryReason),
                trigger = trigger
            )
        }
    }

    fun updateWatch(watch: WatchSnapshot) {
        _state.update { it.copy(watch = watch) }
    }

    fun markPendingSave(component: ComponentId) {
        _state.update { it.copy(pendingSaves = it.pendingSaves + component) }
    }

    fun clearPendingSave(component: ComponentId) {
        _state.update { it.copy(pendingSaves = it.pendingSaves - component) }
    }

    fun isPendingSave(component: ComponentId): Boolean =
        _state.value.isPendingSave(component)

    fun registerCloseHook(hook: () -> Unit) {
        closeHooks.add(hook)
    }

    fun close(reason: String = "Disconnected") {
        if (_state.value.phase is SessionPhase.Closed) return

        Timber.i("Closing Session $id: $reason")
        _state.update {
            it.copy(
                phase = SessionPhase.Closed(reason),
                pendingSaves = emptySet()
            )
        }

        closeHooks.forEach { hook ->
            runCatching { hook() }.onFailure { e -> Timber.e(e, "Error executing session close hook") }
        }

        device?.let {
            runCatching {
                repository.teardownConnection(it)
            }.onFailure { e ->
                Timber.e(e, "Error tearing down GShockAPI connection in Session")
            }
        }

        sessionScope.cancel("Session closed: $reason")
    }
}
