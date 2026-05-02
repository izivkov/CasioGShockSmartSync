package com.beamburst.casswatch.ui.settings

import AppSwitch
import com.beamburst.casswatch.ui.common.AppText
import com.beamburst.casswatch.ui.common.AppTextLarge
import com.beamburst.casswatch.ui.common.AppTextLink
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.ui.common.InfoButton
import com.beamburst.casswatch.ui.common.ValueSelectionDialog
import org.avmedia.gshockapi.WatchInfo
import androidx.compose.runtime.mutableIntStateOf
import com.beamburst.casswatch.theme.Spacing

@Composable
fun TimeAdjustment(
    onUpdate: (SettingsViewModel.TimeAdjustment) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val classType = SettingsViewModel.TimeAdjustment::class.java
    val state by settingsViewModel.state.collectAsState()
    val timeAdjustmentSetting: SettingsViewModel.TimeAdjustment =
        settingsViewModel.getSetting(classType)

    var timeAdjustment by remember { mutableStateOf(timeAdjustmentSetting.timeAdjustment) }
    var notifyMe by remember { mutableStateOf(timeAdjustmentSetting.timeAdjustmentNotifications) }
    var adjustmentMinutes by remember { mutableStateOf(timeAdjustmentSetting.adjustmentTimeMinutes.toString()) }
    var fineAdjustment by remember { mutableIntStateOf(timeAdjustmentSetting.fineAdjustment) }

    LaunchedEffect(state) {
        timeAdjustment = timeAdjustmentSetting.timeAdjustment
        notifyMe = timeAdjustmentSetting.timeAdjustmentNotifications
        adjustmentMinutes = timeAdjustmentSetting.adjustmentTimeMinutes.toString()
        fineAdjustment = timeAdjustmentSetting.fineAdjustment
    }

    SettingCard(modifier = Modifier.fillMaxWidth()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            if (WatchInfo.alwaysConnected) {
                FineAdjustmentRow(
                    modifier = Modifier
                        .padding(top = Spacing.xs),
                    value = fineAdjustment,
                    onValueChange = { newValue ->
                        fineAdjustment = newValue
                        onUpdate(timeAdjustmentSetting.copy(fineAdjustment = newValue))
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextLarge(
                        text = stringResource(
                            id = R.string.time_adjustment
                        ),
                        modifier = Modifier.padding(end = Spacing.sm)
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
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = stringResource(
                            id = R.string.adjustment_time_minutes
                        ),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = Spacing.sm)
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
                            .padding(Spacing.sm),
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
                        .padding(top = Spacing.xs),
                    value = fineAdjustment,
                    onValueChange = { newValue ->
                        fineAdjustment = newValue
                        onUpdate(timeAdjustmentSetting.copy(fineAdjustment = newValue))
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xxs),
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
