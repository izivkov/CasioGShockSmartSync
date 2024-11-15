package org.avmedia.gShockSmartSyncCompose.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.R

@Composable
fun PrayerAlarmsView(
    onUpdate: (ActionsViewModel.PrayerAlarmsAction) -> Unit,
    actionsViewModel: ActionsViewModel = viewModel(),
) {
    val classType = ActionsViewModel.PrayerAlarmsAction::class.java

    val actions by actionsViewModel.actions.collectAsState()
    val prayerAlarmsAction: ActionsViewModel.PrayerAlarmsAction =
        actionsViewModel.getAction(classType)

    var isEnabled by remember { mutableStateOf(prayerAlarmsAction.enabled) }

    LaunchedEffect(actions, prayerAlarmsAction) {
        isEnabled = prayerAlarmsAction.enabled
    }

    ActionItem(
        title = stringResource(id = R.string.set_prayer_alarms),
        resourceId = R.drawable.prayer_times,
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue // Update the state when the switch is toggled
            prayerAlarmsAction.enabled = newValue
            onUpdate(prayerAlarmsAction.copy(enabled = isEnabled))
        },
        infoText = stringResource(id = R.string.prayer_times_info)
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewPrayerAction() {
    PrayerAlarmsView(onUpdate = {})
}

