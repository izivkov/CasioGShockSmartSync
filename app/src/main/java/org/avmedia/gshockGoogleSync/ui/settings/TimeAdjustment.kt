package org.avmedia.gshockGoogleSync.ui.settings

import AppSwitch
import AppText
import AppTextLarge
import AppTextLink
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockGoogleSync.ui.common.ValueSelectionDialog
import org.avmedia.gshockapi.WatchInfo

@Composable
fun TimeAdjustment(
    onUpdate: (SettingsViewModel.TimeAdjustment) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val classType = SettingsViewModel.TimeAdjustment::class.java
    val settings by settingsViewModel.settings.collectAsState()
    val timeAdjustmentSetting: SettingsViewModel.TimeAdjustment =
        settingsViewModel.getSetting(classType)

    var timeAdjustment by remember { mutableStateOf(timeAdjustmentSetting.timeAdjustment) }

    var notifyMe by remember { mutableStateOf(timeAdjustmentSetting.timeAdjustmentNotifications) }
    var adjustmentMinutes by remember { mutableStateOf(timeAdjustmentSetting.adjustmentTimeMinutes.toString()) }

    LaunchedEffect(settings, timeAdjustment, notifyMe) {
        timeAdjustment = timeAdjustmentSetting.timeAdjustment
        notifyMe = timeAdjustmentSetting.timeAdjustmentNotifications
        adjustmentMinutes = timeAdjustmentSetting.adjustmentTimeMinutes.toString()
    }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp)
        ) {
            if (WatchInfo.alwaysConnected) {
                FineAdjustmentRow(
                    modifier = Modifier
                        .padding(end = 12.dp, start = 12.dp, top = 6.dp),
                    onUpdate = onUpdate,
                )

            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextLarge(
                        text = stringResource(
                            id = R.string.time_adjustment
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    InfoButton(
                        infoText = stringResource(
                            id = R.string.time_adjustment_info
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AppSwitch(
                        checked = timeAdjustment,
                        onCheckedChange = { newValue ->
                            timeAdjustment = newValue // Update the state when the switch is toggled
                            timeAdjustmentSetting.timeAdjustment = newValue
                            onUpdate(timeAdjustmentSetting.copy(timeAdjustment = newValue))
                        },
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(end = 12.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = stringResource(
                            id = R.string.adjustment_time_minutes
                        ),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    InfoButton(
                        infoText = stringResource(
                            id = R.string.adjustment_time_info
                        )
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    var showDialog by remember { mutableStateOf(false) }

                    AppTextLink(
                        text = "$adjustmentMinutes m",
                        modifier = Modifier
                            .clickable { showDialog = true }
                            .padding(6.dp),
                    )
                    if (showDialog) {
                        ValueSelectionDialog(
                            initialValue = adjustmentMinutes.toInt(),
                            range = 0..59,
                            onDismiss = { showDialog = false },
                            onConfirm = { newValue ->
                                showDialog = false
                                onUpdate(
                                    timeAdjustmentSetting.copy(
                                        adjustmentTimeMinutes = newValue
                                    )
                                )
                            },
                            title = stringResource(
                                R.string.when_to_run
                            ),
                            label = stringResource(
                                R.string.minutes_between_0_and_59
                            ),
                            unit = "m"
                        )
                    }
                }

                FineAdjustmentRow(
                    modifier = Modifier
                        .padding(end = 12.dp, start = 12.dp, top = 6.dp),
                    onUpdate = onUpdate,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = stringResource(
                            id = R.string.notify_me
                        ),
                        modifier = Modifier.wrapContentWidth(),
                    )
                    Checkbox(
                        checked = notifyMe,
                        onCheckedChange = { newValue ->
                            notifyMe = newValue // Update the state when the switch is toggled
                            timeAdjustmentSetting.timeAdjustmentNotifications = newValue
                            onUpdate(timeAdjustmentSetting.copy(timeAdjustmentNotifications = newValue))
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTimeAdjustment() {
    TimeAdjustment(onUpdate = {})
}


