package org.avmedia.gshockGoogleSync.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppSwitchWithText
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

@Composable
fun AlarmChimeSwitch(modifier: Modifier) {
    val isChecked =
        remember { mutableStateOf(AlarmsModel.getAlarms().getOrNull(0)?.hasHourlyChime ?: false) }

    val onAlarmsLoaded: () -> Unit = {
        isChecked.value = AlarmsModel.getAlarms().getOrNull(0)?.hasHourlyChime ?: false
    }

    val alarmsLoadedEvent: String = "Alarms Loaded"

    LaunchedEffect(alarmsLoadedEvent) {
        ProgressEvents.runEventActions(
            name = alarmsLoadedEvent,
            eventActions = listOf(EventAction(alarmsLoadedEvent, onAlarmsLoaded)).toTypedArray()
        )
    }

    Column(
        modifier = Modifier
            .padding(0.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.End,
    ) {
        AppSwitchWithText(
            isChecked = isChecked.value,
            onCheckedChange = { checked ->
                if (!AlarmsModel.isEmpty()) {
                    val currentAlarm = AlarmsModel.getAlarms()[0]
                    val updatedAlarm = currentAlarm.copy(hasHourlyChime = checked)
                    AlarmsModel.updateAlarm(0, updatedAlarm)
                    isChecked.value = checked
                }
            },
            modifier = modifier,
            text = stringResource(id = R.string.signal_chime)
        )
    }
}