package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: GShockRepository,
    private val actionContainer: ActionContainer
) {
    fun setupActionSubscriptions() {
        val buttonActions = arrayOf(
            EventAction("ButtonPressedInfoReceived") {
                when {
                    repository.isActionButtonPressed() ->
                        actionContainer.runActionsForActionButton(context)

                    repository.isAutoTimeStarted() ->
                        actionContainer.runActionsForAutoTimeSetting(context)

                    repository.isFindPhoneButtonPressed() ->
                        actionContainer.runActionFindPhone(context)

                    repository.isNormalButtonPressed() ->
                        actionContainer.runActionForConnection(context)

                    repository.isAlwaysConnectedConnectionPressed() ->
                        actionContainer.runActionForAlwaysConnected(context)
                }
            }
        )
        ProgressEvents.runEventActions(Utils.AppHashCode(), buttonActions)

        // Triggered by messages rather than button presses, e.g. "FindPhone" on always-connected watches
        val otherActions = arrayOf(
            EventAction("RunActions") {
                actionContainer.runActionsForActionButton(context)
            }
        )
        ProgressEvents.runEventActions(Utils.AppHashCode(), otherActions)
        Timber.i("Action subscriptions registered at process scope (no UI required)")
    }
}
