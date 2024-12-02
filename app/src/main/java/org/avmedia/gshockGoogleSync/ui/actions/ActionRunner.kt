package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
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
        EventAction("RunActions") {
            actionsViewModel.runActionsForActionButton(context)
        },
        EventAction("ButtonPressedInfoReceived") {
            when {
                api.isActionButtonPressed() -> {
                    actionsViewModel.runActionsForActionButton(context)
                }

                api.isAutoTimeStarted() -> {
                    actionsViewModel.runActionsForAutoTimeSetting(context)
                }

                api.isFindPhoneButtonPressed() -> {
                    actionsViewModel.runActionFindPhone(context)
                }

                api.isNormalButtonPressed() -> {
                    actionsViewModel.runActionForConnection(context)
                }
            }
        },
    )

    ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
}
