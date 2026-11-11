package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.session.ConnectionButton
import org.avmedia.gshockGoogleSync.session.Session
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.session.SessionPhase
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
        // Everything here is driven by the Session: actions run once per Session when it is Ready,
        // and again whenever the watch asks for them. There is no ordering dependency on other
        // subscribers of the connection events.
        scope.launch {
            sessionManager.currentSession.filterNotNull().collect { session ->
                launch { runActionsWhenReady(session) }
                launch { runActionsOnRequest(session) }
            }
        }
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

    /**
     * Runs the actions each time an always-connected watch requests it (the session's
     * `isRunningActions` turns on), and clears the flag when they have been started. Ends with the
     * session.
     */
    private suspend fun runActionsOnRequest(session: Session) {
        session.state
            .takeWhile { it.phase !is SessionPhase.Closed }
            .map { it.isRunningActions }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                actionContainer.runActionsForActionButton(context) { session.endRunActions() }
            }
    }
}