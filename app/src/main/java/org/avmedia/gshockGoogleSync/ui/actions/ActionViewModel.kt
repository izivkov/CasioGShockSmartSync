package org.avmedia.gshockGoogleSync.ui.actions

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.session.ComponentId
import org.avmedia.gshockGoogleSync.session.SessionManager
import javax.inject.Inject

/**
 * Thin, screen-scoped facade over [org.avmedia.gshockGoogleSync.ActionContainer] for Compose screens.
 *
 * All actual action state, dispatch logic, and Action subclasses live in
 * [org.avmedia.gshockGoogleSync.ActionContainer], a @Singleton with no Android lifecycle - it has to survive
 * background watch button presses even when no UI is composed. This class exists
 * only so Compose screens can obtain it the normal way, via hiltViewModel(),
 * instead of the @EntryPoint workaround that was needed while this name was
 * itself the singleton (see git history / ActionsProvider.kt, now removed).
 *
 * Background singletons (ActionRunner, VoiceDispatcher, VoiceCommandTable) and
 * other feature ViewModels needing DIRECT_INVOCATION access (SettingsViewModel,
 * AlarmViewModel, TimeViewModel, EventViewModel) inject ActionContainer directly -
 * they have no screen lifecycle to scope this wrapper to.
 */
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val container: ActionContainer,
    private val draft: ActionsDraft,
    sessionManager: SessionManager,
) : ViewModel() {

    val actions: StateFlow<List<ActionContainer.Action>> = container.actions

    /** True while the user changed something that is not yet saved. */
    val isModified: StateFlow<Boolean> = sessionManager.isPendingSaveFlow(ComponentId.ACTIONS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.isPendingSave(ComponentId.ACTIONS))

    fun <T : ActionContainer.Action> getAction(type: Class<T>): T = container.getAction(type)

    // Edits go to the session's draft; nothing is applied until save().
    fun <T : ActionContainer.Action> isEnabled(type: Class<T>): Boolean = draft.isEnabled(type)
    fun <T : ActionContainer.Action> setEnabled(type: Class<T>, enabled: Boolean) =
        draft.setEnabled(type, enabled)

    fun cameraOrientation() = draft.cameraOrientation()
    fun setCameraOrientation(orientation: ActionContainer.CameraOrientation) =
        draft.setCameraOrientation(orientation)

    fun phoneNumber() = draft.phoneNumber()
    fun setPhoneNumber(number: String) = draft.setPhoneNumber(number)

    fun save() = draft.save()

    override fun onCleared() = container.onCleared()
}
