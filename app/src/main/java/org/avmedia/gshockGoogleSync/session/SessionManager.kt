package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level Singleton manager providing the single source of truth for watch connection sessions.
 * Manages [Session] instances, tracks pending saves, and exposes reactive flows for components.
 */
@Singleton
class SessionManager @Inject constructor(
    private val repository: GShockRepository
) {
    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    fun evaluateConnectionTrigger(): ConnectionTrigger =
        _currentSession.value?.evaluateConnectionTrigger() ?: when {
            repository.isActionButtonPressed() -> ConnectionTrigger.ACTION_BUTTON
            repository.isAutoTimeStarted() -> ConnectionTrigger.AUTO_TIME
            repository.isFindPhoneButtonPressed() -> ConnectionTrigger.FIND_PHONE
            repository.isAlwaysConnectedConnectionPressed() -> ConnectionTrigger.ALWAYS_CONNECTED
            else -> ConnectionTrigger.NORMAL
        }

    fun createSession(device: BluetoothDevice? = null): Session {
        val sessionId = UUID.randomUUID().toString()
        Timber.i("Creating new watch Session: $sessionId")

        _currentSession.value?.close("New session started")

        val trigger = evaluateConnectionTrigger()
        val newSession = Session(
            id = sessionId,
            repository = repository,
            device = device,
            initialTrigger = trigger
        )
        _currentSession.value = newSession
        _sessionState.value = newSession.state.value

        return newSession
    }

    fun onConnected(entryReason: EntryReason = EntryReason.NORMAL) {
        val session = _currentSession.value ?: createSession()
        val trigger = evaluateConnectionTrigger()
        session.setReady(entryReason, trigger)
        _sessionState.value = session.state.value
    }

    fun onDisconnected(device: BluetoothDevice? = null, reason: String = "Watch disconnected") {
        val session = _currentSession.value
        if (session != null) {
            session.close(reason)
            _sessionState.value = session.state.value
        } else {
            _sessionState.value = SessionState(phase = SessionPhase.Disconnected)
        }
    }

    fun markPendingSave(component: ComponentId) {
        _currentSession.value?.markPendingSave(component)
        _sessionState.update { it.copy(pendingSaves = it.pendingSaves + component) }
    }

    fun clearPendingSave(component: ComponentId) {
        _currentSession.value?.clearPendingSave(component)
        _sessionState.update { it.copy(pendingSaves = it.pendingSaves - component) }
    }

    fun isPendingSave(component: ComponentId): Boolean =
        _sessionState.value.isPendingSave(component)

    fun isPendingSaveFlow(component: ComponentId) =
        _sessionState.map { it.isPendingSave(component) }
}
