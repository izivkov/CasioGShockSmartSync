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
    private val actionContainer: ActionContainer,
    private val h5600ScheduleRunner: org.avmedia.gshockGoogleSync.h5600.H5600ScheduleRunner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var findPhoneEndedSubscription: AutoCloseable? = null

    fun setupActionSubscriptions() {
        // Everything here is driven by the Session: actions run once per Session when it is Ready,
        // and again whenever the watch asks for them. There is no ordering dependency on other
        // subscribers of the connection events.
        scope.launch {
            sessionManager.currentSession.filterNotNull().collect { session ->
                launch { runActionsWhenReady(session) }
                launch { runActionsOnRequest(session) }
                launch { h5600ScheduleRunner.applyAtBoundariesWhileConnected(session) }
            }
        }
        // Back button on the watch's Find Phone screen ("0x0A 00") stops the ring.
        if (findPhoneEndedSubscription == null) {
            findPhoneEndedSubscription = org.avmedia.gshockapi.ProgressEvents.subscribe(
                arrayOf(
                    org.avmedia.gshockapi.EventAction("FindPhoneEnded") {
                        if (PhoneFinder.isRinging) PhoneFinder.stop(context)
                    },
                ),
            )
        }
        Timber.i("Action subscriptions registered at process scope (no UI required)")
    }

    private suspend fun runActionsWhenReady(session: Session) {
        val state = session.state.first { it.phase !is SessionPhase.Connecting }
        if (state.phase !is SessionPhase.Ready) return

        // Find Phone rings at once: the H5600 connect routine below (handshake + loc/time sync) can
        // take 30 s+, and the watch may end the find connection before then, which stops the ring.
        if (state.button == ConnectionButton.FIND) {
            Timber.i("Find Phone pressed: ringing before the H5600 connect routine")
            actionContainer.runActionFindPhone(context)
            h5600ScheduleRunner.onConnected(applySchedule = false)
            return
        }

        // DW-H5600: 0x39 keep-connected handshake, then per-alarm days / sound. Runs first, because
        // the regular actions may end the connection (e.g. after an automatic time sync).
        // No-op for other watches.
        h5600ScheduleRunner.onConnected(applySchedule = state.button != null)

        // DW-H5600: the scheduler above already did the connect work (time sync — postponed while
        // an alarm may be snoozing, since a time sync cancels the snooze — and the alarm slots).
        // The stock connect actions would sync the time again regardless, and their prayer alarms
        // would fight the phone-side alarm list; reminders are not supported on this watch.
        if (org.avmedia.gshockapi.WatchInfo.model == org.avmedia.gshockapi.WatchInfo.WatchModel.DW_H5600 &&
            state.button != ConnectionButton.FROM_ACTION
        ) {
            Timber.i("DW-H5600: stock connect actions skipped (${state.button})")
            return
        }

        when (state.button) {
            ConnectionButton.FROM_ACTION -> actionContainer.runActionsForActionButton(context)
            ConnectionButton.AUTOMATIC_SYNC -> actionContainer.runActionsForAutoTimeSetting(context)
            ConnectionButton.FIND -> Unit // handled above, before the connect routine
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
                // DW-H5600: stays connected (0x39 handshake), so its Find Phone press arrives as
                // "0x0A 02" on the open link instead of as a new FIND connection. The Actions tab is
                // hidden in this fork, so the press always means: ring the phone.
                if (org.avmedia.gshockapi.WatchInfo.model == org.avmedia.gshockapi.WatchInfo.WatchModel.DW_H5600) {
                    Timber.i("DW-H5600 Find Phone pressed while connected: ringing")
                    actionContainer.runActionFindPhone(context)
                    session.endRunActions()
                } else {
                    actionContainer.runActionsForActionButton(context) { session.endRunActions() }
                }
            }
    }
}