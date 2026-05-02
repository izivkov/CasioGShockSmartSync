package com.beamburst.casswatch.ui.alarms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.ui.common.AppSnackbar
import com.beamburst.casswatch.ui.common.ButtonData
import com.beamburst.casswatch.ui.common.ButtonsRow
import com.beamburst.casswatch.ui.common.ItemView
import com.beamburst.casswatch.ui.common.ScreenTitle

@Composable
fun AlarmList(alarmViewModel: AlarmViewModel = hiltViewModel()) {
    val alarms by alarmViewModel.alarms.collectAsState()
    val viewMode by alarmViewModel.viewMode.collectAsState()
    val alarmDays by alarmViewModel.alarmDays.collectAsState()

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
                        },
                        showDaySelector = viewMode == AlarmViewMode.WEEKLY,
                        selectedDays = alarmDays[index] ?: emptySet(),
                        onDayToggled = { day -> alarmViewModel.toggleDay(index, day) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmsScreen(alarmViewModel: AlarmViewModel = hiltViewModel()) {
    val viewMode by alarmViewModel.viewMode.collectAsState()

    LaunchedEffect(Unit) {
        alarmViewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    AppSnackbar(event.message)
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (title, modeToggle, alarms, buttonsRow) = createRefs()

            ScreenTitle(
                text = stringResource(id = R.string.watch_alarms),
                modifier = Modifier.constrainAs(title) {
                    top.linkTo(parent.top)
                    bottom.linkTo(modeToggle.top)
                }
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .constrainAs(modeToggle) {
                        top.linkTo(title.bottom)
                        bottom.linkTo(alarms.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SegmentedButton(
                    selected = viewMode == AlarmViewMode.SIMPLE,
                    onClick = { alarmViewModel.setViewMode(AlarmViewMode.SIMPLE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(stringResource(R.string.alarm_view_simple)) }
                )
                SegmentedButton(
                    selected = viewMode == AlarmViewMode.WEEKLY,
                    onClick = { alarmViewModel.setViewMode(AlarmViewMode.WEEKLY) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(stringResource(R.string.alarm_view_weekly)) }
                )
            }

            Column(
                modifier = Modifier
                    .constrainAs(alarms) {
                        top.linkTo(modeToggle.bottom)
                        bottom.linkTo(buttonsRow.top)
                        height = Dimension.fillToConstraints
                    }
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                AlarmList(alarmViewModel)
                AlarmChimeSwitch(
                    onUpdate = { isChecked -> alarmViewModel.toggleHourlyChime(isChecked) }
                )
            }

            val buttons = listOf(
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
                modifier = Modifier
                    .constrainAs(buttonsRow) {
                        top.linkTo(alarms.bottom)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
                    .fillMaxWidth()
            )
        }
    }
}
