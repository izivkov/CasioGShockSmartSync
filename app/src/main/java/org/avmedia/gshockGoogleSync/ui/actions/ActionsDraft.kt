package org.avmedia.gshockGoogleSync.ui.actions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.ActionContainer.Action
import org.avmedia.gshockGoogleSync.session.ComponentId
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.session.SessionPhase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The changes the user made on the Actions screen but has not saved yet.
 *
 * The draft lives as long as the connection session: switching screens keeps it, and it is
 * dropped when the session closes (or a new one starts). Until [save] is called the real
 * actions, which the background runner uses, are not touched.
 *
 * Only values that differ from the saved ones are kept, so changing a switch and changing it
 * back leaves nothing pending.
 */
@Singleton
class ActionsDraft @Inject constructor(
    private val container: ActionContainer,
    private val sessionManager: SessionManager,
) {
    private data class Changes(
        val enabled: Map<Class<out Action>, Boolean> = emptyMap(),
        val cameraOrientation: ActionContainer.CameraOrientation? = null,
        val phoneNumber: String? = null,
    ) {
        val isEmpty: Boolean
            get() = enabled.isEmpty() && cameraOrientation == null && phoneNumber == null
    }

    private val changes = MutableStateFlow(Changes())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            sessionManager.currentSession.collectLatest { session ->
                discard()
                session?.state?.first { it.phase is SessionPhase.Closed }
                discard()
            }
        }
    }

    fun <T : Action> isEnabled(type: Class<T>): Boolean =
        changes.value.enabled[type] ?: container.getAction(type).enabled

    fun <T : Action> setEnabled(type: Class<T>, enabled: Boolean) {
        val saved = container.getAction(type).enabled
        change {
            it.copy(enabled = if (enabled == saved) it.enabled - type else it.enabled + (type to enabled))
        }
    }

    fun cameraOrientation(): ActionContainer.CameraOrientation =
        changes.value.cameraOrientation
            ?: container.getAction(ActionContainer.PhotoAction::class.java).cameraOrientation

    fun setCameraOrientation(orientation: ActionContainer.CameraOrientation) {
        val saved = container.getAction(ActionContainer.PhotoAction::class.java).cameraOrientation
        change { it.copy(cameraOrientation = orientation.takeIf { o -> o != saved }) }
    }

    fun phoneNumber(): String =
        changes.value.phoneNumber
            ?: container.getAction(ActionContainer.PhoneDialAction::class.java).phoneNumber

    fun setPhoneNumber(number: String) {
        val saved = container.getAction(ActionContainer.PhoneDialAction::class.java).phoneNumber
        change { it.copy(phoneNumber = number.takeIf { n -> n != saved }) }
    }

    /** Applies the draft to the actions, persists them, and clears the pending-save mark. */
    fun save() {
        val pending = changes.value
        val touched = LinkedHashSet<Class<out Action>>()

        pending.enabled.forEach { (type, enabled) ->
            container.getAction(type).enabled = enabled
            touched += type
        }
        pending.cameraOrientation?.let {
            container.getAction(ActionContainer.PhotoAction::class.java).cameraOrientation = it
            touched += ActionContainer.PhotoAction::class.java
        }
        pending.phoneNumber?.let {
            container.getAction(ActionContainer.PhoneDialAction::class.java).phoneNumber = it
            touched += ActionContainer.PhoneDialAction::class.java
        }
        touched.forEach { container.updateAction(container.getAction(it)) }

        discard()
    }

    private fun change(transform: (Changes) -> Changes) {
        changes.update(transform)
        if (changes.value.isEmpty) {
            sessionManager.clearPendingSave(ComponentId.ACTIONS)
        } else {
            sessionManager.markPendingSave(ComponentId.ACTIONS)
        }
    }

    private fun discard() {
        changes.value = Changes()
        sessionManager.clearPendingSave(ComponentId.ACTIONS)
    }
}
