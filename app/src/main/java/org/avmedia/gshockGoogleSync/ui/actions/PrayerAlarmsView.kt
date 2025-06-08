package org.avmedia.gshockGoogleSync.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R

@Composable
fun PrayerAlarmsView(
    onUpdate: (ActionsViewModel.PrayerAlarmsAction) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel()
) {
    val prayerAlarmsAction = remember {
        actionsViewModel.getAction(ActionsViewModel.PrayerAlarmsAction::class.java)
    }

    var isEnabled by remember { mutableStateOf(prayerAlarmsAction.enabled) }

    ActionItem(
        title = stringResource(id = R.string.set_prayer_alarms),
        resourceId = R.drawable.prayer_times,
        infoText = stringResource(id = R.string.prayer_times_info),
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue
            prayerAlarmsAction.enabled = newValue
            onUpdate(prayerAlarmsAction.copy(enabled = newValue))
        }
    )
}
