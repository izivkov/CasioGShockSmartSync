package org.avmedia.gShockSmartSyncCompose.ui.alarms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.theme.GShockSmartSyncTheme
import org.avmedia.gShockSmartSyncCompose.ui.common.ButtonData
import org.avmedia.gShockSmartSyncCompose.ui.common.ButtonsRow
import org.avmedia.gShockSmartSyncCompose.ui.common.ItemList
import org.avmedia.gShockSmartSyncCompose.ui.common.ItemView
import org.avmedia.gShockSmartSyncCompose.ui.common.ScreenTitle

@Composable
fun AlarmList(alarmViewModel: AlarmViewModel = viewModel()) {
    val alarms by alarmViewModel.alarms.collectAsState()

    LaunchedEffect(alarms) {
    }

    @Composable
    fun createItemList(): List<Any> {
        val alarmItems = mutableListOf<Any>()

        alarms.forEachIndexed { index, alarm ->
            println("Alarm ${index}: ${alarm.enabled}")
            ItemView {
                AlarmItem(
                    hours = alarm.hour,
                    minutes = alarm.minute,
                    isAlarmEnabled = alarm.enabled,
                    onToggleAlarm = { isEnabled ->
                        alarmViewModel.toggleAlarm(index, isEnabled)
                    },
                    onTimeChanged = { hours, munutes ->
                        alarmViewModel.onTimeChanged(index, hours, munutes)
                    }
                )
            }
        }

        return alarmItems.toList()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ItemList(createItemList())
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlarmsScreen(
    alarmViewModel: AlarmViewModel = viewModel()
) {
    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            ConstraintLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val (title, alarms, buttonsRow) = createRefs()

                ScreenTitle(stringResource(id = R.string.watch_alarms), Modifier
                    .constrainAs(title) {
                        top.linkTo(parent.top)  // Link top of content to parent top
                        bottom.linkTo(alarms.top)  // Link bottom of content to top of buttonsRow
                    })

                // Scrollable part
                Column(
                    modifier = Modifier
                        .constrainAs(alarms) {
                            top.linkTo(title.bottom)
                            bottom.linkTo(buttonsRow.top)
                            height = Dimension.fillToConstraints
                        }
                        .verticalScroll(rememberScrollState())  // Make content scrollable
                        .padding(0.dp)
                        .fillMaxWidth()
                        .fillMaxSize()
                ) {
                    AlarmList()

                    AlarmChimeSwitch(
                        modifier = Modifier
                            .padding(4.dp)
                    )
                }

                // Column for ButtonsRow at the bottom
                Column(
                    modifier = Modifier
                        .constrainAs(buttonsRow) {
                            top.linkTo(alarms.bottom)  // Link top of buttonsRow to bottom of content
                            bottom.linkTo(parent.bottom)  // Keep buttons at the bottom
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }
                        .fillMaxWidth()
                        .padding(0.dp)
                ) {
                    val buttons = arrayListOf(
                        ButtonData(
                            text = stringResource(id = R.string.send_alarms_to_phone),
                            onClick = { alarmViewModel.sendAlarmsToPhone() }),
                        ButtonData(
                            text = stringResource(id = R.string.send_alarms_to_watch),
                            onClick = {
                                alarmViewModel.sendAlarmsToWatch()
                            })
                    )

                    ButtonsRow(buttons = buttons)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAlarmScreen() {
    AlarmsScreen()
}