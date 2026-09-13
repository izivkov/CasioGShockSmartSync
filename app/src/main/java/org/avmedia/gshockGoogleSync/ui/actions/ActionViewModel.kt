package org.avmedia.gshockGoogleSync.ui.actions
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin, screen-scoped facade over [ActionContainer] for Compose screens.
 *
 * All actual action state, dispatch logic, and Action subclasses live in
 * [ActionContainer], a @Singleton with no Android lifecycle - it has to survive
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
    private val container: ActionContainer
) : ViewModel() {

    val actions: StateFlow<List<ActionContainer.Action>> = container.actions
    val uiEvents: SharedFlow<ActionContainer.UiEvent> = container.uiEvents

    fun <T : ActionContainer.Action> getAction(type: Class<T>): T = container.getAction(type)

    fun <T : ActionContainer.Action> updateAction(updatedAction: T) =
        container.updateAction(updatedAction)

    fun save() = container.onCleared()

    override fun onCleared() = container.onCleared()
}
