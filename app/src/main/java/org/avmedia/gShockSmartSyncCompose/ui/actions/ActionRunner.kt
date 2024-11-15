package org.avmedia.gShockSmartSyncCompose.ui.actions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.api
import org.avmedia.gShockSmartSyncCompose.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

@Composable
fun ActionRunner(
    context: Context,
    actionsViewModel: ActionsViewModel = viewModel()
) {
    val eventActions = arrayOf(
        EventAction("RunActions") {
            actionsViewModel.runActionsForActionButton(context)
        },
        EventAction("ButtonPressedInfoReceived") {
            when {
                api().isActionButtonPressed() -> {
                    actionsViewModel.runActionsForActionButton(context)
                }

                api().isAutoTimeStarted() -> {
                    actionsViewModel.runActionsForAutoTimeSetting(context)
                }

                api().isFindPhoneButtonPressed() -> {
                    actionsViewModel.runActionFindPhone(context)
                }

                api().isNormalButtonPressed() -> {
                    actionsViewModel.runActionForConnection(context)
                }
            }
        },
    )

    ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
}
