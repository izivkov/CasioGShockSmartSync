package com.beamburst.casswatch.ui.alarms

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.ui.common.AppButton
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
    val firedAts by alarmViewModel.firedAts.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        alarms.forEachIndexed { index, alarm ->
            key(index) {
                ItemView {
                    AlarmItem(
                        hours = alarm.hour,
                        minutes = alarm.minute,
                        isAlarmEnabled = alarm.enabled,
                        showAsEnabled = alarm.enabled && !firedAts.containsKey(index),
                        name = alarm.name,
                        onToggleAlarm = { isEnabled ->
                            alarmViewModel.toggleAlarm(index, isEnabled)
                        },
                        onTap = { alarmViewModel.openEditor(index) },
                        showDaySelector = viewMode == AlarmViewMode.WEEKLY,
                        selectedDays = alarmDays[index] ?: emptySet()
                    )
                }
            }
        }
    }
}

@Composable
private fun ExactAlarmPermissionDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exact_alarm_dialog_title)) },
        text = { Text(stringResource(R.string.exact_alarm_dialog_body)) },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.exact_alarm_dialog_settings),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    onOpenSettings()
                }
            )
        },
        dismissButton = {
            AppButton(
                text = stringResource(R.string.exact_alarm_dialog_not_now),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun AlarmsActionRow(alarmViewModel: AlarmViewModel) {
    val isConnected by alarmViewModel.isConnected.collectAsState()

    if (isConnected) {
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
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            rowPadding = Spacing.sm
        )
    } else {
        DisconnectedInfoCard(
            onSendToPhone = { alarmViewModel.sendAlarmsToPhone() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(alarmViewModel: AlarmViewModel = hiltViewModel()) {
    val viewMode by alarmViewModel.viewMode.collectAsState()
    val editorTarget by alarmViewModel.editorTarget.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        alarmViewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    AppSnackbar(event.message)
                }
                is UiEvent.RequestExactAlarmPermission -> showPermissionDialog = true
            }
        }
    }

    if (showPermissionDialog) {
        ExactAlarmPermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onOpenSettings = { showPermissionDialog = false }
        )
    }

    editorTarget?.let { draft ->
        AlarmEditorSheet(
            draft = draft,
            sheetState = sheetState,
            onSave = { hour, minute, name, days ->
                alarmViewModel.upsertAlarm(draft.index, hour, minute, name, days)
            },
            onDismiss = { alarmViewModel.dismissEditor() }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ScreenTitle(
                    text = stringResource(id = R.string.watch_alarms),
                    modifier = Modifier
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
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

                AlarmList(alarmViewModel)
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    AlarmChimeSwitch(
                        onUpdate = { isChecked -> alarmViewModel.toggleHourlyChime(isChecked) }
                    )
                    AlarmsActionRow(alarmViewModel = alarmViewModel)
                }
            }

            AlarmsFooter(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                alarmViewModel = alarmViewModel
            )
        }
    }
}
