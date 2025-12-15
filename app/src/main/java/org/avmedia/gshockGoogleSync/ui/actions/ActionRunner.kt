package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import javax.inject.Named

@Composable
fun ActionRunner(
    context: Context,
    actionsViewModel: ActionsViewModel = hiltViewModel(),
    @Named("api") api: GShockRepository
) {
    val eventActions = arrayOf(
        EventAction("ButtonPressedInfoReceived") {
            when {
                api.isActionButtonPressed() -> actionsViewModel.runActionsForActionButton(context)
                api.isAutoTimeStarted() -> actionsViewModel.runActionsForAutoTimeSetting(context)
                api.isFindPhoneButtonPressed() -> actionsViewModel.runActionFindPhone(context)
                api.isNormalButtonPressed() -> actionsViewModel.runActionForConnection(context)
                api.isAlwaysConnectedConnectionPressed() -> actionsViewModel.runActionForAlwaysConnected(
                    context
                )
            }
        }
    )
    ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)

    // Other actions that are triggered by sending messages, like "FindPhone" for always connected devices
    val otherActions = arrayOf(
        EventAction("RunActions") {
            actionsViewModel.runActionsForActionButton(context)
        },
    )

    ProgressEvents.runEventActions(Utils.AppHashCode() + "otherActions", otherActions)
}
