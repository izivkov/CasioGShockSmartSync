package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.session.ConnectionButton
import org.avmedia.gshockGoogleSync.session.Session
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.session.SessionPhase
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val actionContainer: ActionContainer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun setupActionSubscriptions() {
        // Runs the actions for the connection button once per Session, as soon as it is Ready.
        // The button comes from the Session, so there is no ordering dependency on other
        // subscribers of the connection events.
        scope.launch {
            sessionManager.currentSession.filterNotNull().collect { session ->
                launch { runActionsWhenReady(session) }
            }
        }

        // Triggered by messages rather than button presses, e.g. "FindPhone" on always-connected watches
        val otherActions = arrayOf(
            EventAction("RunActions") {
                actionContainer.runActionsForActionButton(context)
            }
        )
        ProgressEvents.runEventActions(Utils.AppHashCode(), otherActions)
        Timber.i("Action subscriptions registered at process scope (no UI required)")
    }

    private suspend fun runActionsWhenReady(session: Session) {
        val state = session.state.first { it.phase !is SessionPhase.Connecting }
        if (state.phase !is SessionPhase.Ready) return

        when (state.button) {
            ConnectionButton.FROM_ACTION -> actionContainer.runActionsForActionButton(context)
            ConnectionButton.AUTOMATIC_SYNC -> actionContainer.runActionsForAutoTimeSetting(context)
            ConnectionButton.FIND -> actionContainer.runActionFindPhone(context)
            ConnectionButton.NORMAL -> actionContainer.runActionForConnection(context)
            ConnectionButton.ALWAYS_CONNECTED -> actionContainer.runActionForAlwaysConnected(context)
            null -> Unit
        }
    }
}