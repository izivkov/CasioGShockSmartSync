package com.beamburst.casswatch.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R

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
