package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

@Composable
fun ActionRunner(
    context: Context,
    actionsViewModel: ActionsViewModel = hiltViewModel(),
    api: GShockRepository
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
