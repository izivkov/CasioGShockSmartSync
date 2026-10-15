package org.avmedia.gshockGoogleSync.ui.alarms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ItemView
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@Composable
fun AlarmList(alarmViewModel: AlarmViewModel = hiltViewModel()) {
    val alarms by alarmViewModel.alarms.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        alarms.forEachIndexed { index, alarm ->
            key(index) {
                ItemView {
                    AlarmItem(
                            hours = alarm.hour,
                            minutes = alarm.minute,
                            isAlarmEnabled = alarm.enabled,
                            name = alarm.name,
                            onToggleAlarm = { isEnabled ->
                                alarmViewModel.toggleAlarm(index, isEnabled)
                            },
                            onTimeChanged = { hours, minutes ->
                                alarmViewModel.onTimeChanged(index, hours, minutes)
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmsScreen(alarmViewModel: AlarmViewModel = hiltViewModel()) {
    Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTitle(
                    text = stringResource(id = R.string.watch_alarms),
                modifier = Modifier
            )

            Column(
                    modifier =
                    Modifier.weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxWidth()
            ) {
                AlarmList()
                AlarmChimeSwitch(
                        onUpdate = { isChecked -> alarmViewModel.toggleHourlyChime(isChecked) }
                )
            }

            val buttons =
                    listOf(
                            ButtonData(
                                    text = stringResource(id = R.string.send_alarms_to_phone),
                                    onClick = { alarmViewModel.sendAlarmsToPhone() }
                            ),
                            ButtonData(
                                    text = stringResource(id = R.string.send_alarms_to_watch),
                                    onClick = { alarmViewModel.sendAlarmsToWatch() }
                            )
                    )

            ButtonsRow(
                    buttons = buttons,
                    modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
