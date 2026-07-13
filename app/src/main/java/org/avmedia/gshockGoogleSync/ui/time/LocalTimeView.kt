package org.avmedia.gshockGoogleSync.ui.time

import AppText
import AppTextLarge
import RealTimeClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import java.util.TimeZone

@Composable
fun LocalTimeView(
    modifier: Modifier,
    timeModel: TimeViewModel = hiltViewModel()
) {
    val state by timeModel.state.collectAsState()

    AppCard(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // First Column: Local Time and TimeZoneTextView
            Column(
                modifier = Modifier
                    .weight(2f)
                    .padding(horizontal = 12.dp),
            ) {
                AppTextLarge(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(
                        id = R.string.local_time
                    ),
                )

                TextClockComposable(
                    modifier = Modifier.align(Alignment.Start),
                    timeOffset = state.timeOffset
                )

                Box {
                    TimeZoneTextView(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 6.dp),
                        textSize = 16.sp
                    )
                }
            }

            // Second Column: Send Time Button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(0.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SendTimeButton(
                    text = stringResource(
                        id = R.string.send_to_watch
                    ),
                    onClick = {
                        timeModel.onAction(TimeAction.SendTimeToWatch)
                    }
                )
            }
        }
    }
}

@Composable
fun TextClockComposable(
    modifier: Modifier = Modifier,
    timeOffset: Long = 0L
) {
    RealTimeClock(
        modifier = modifier.padding(start = 0.dp),
        offsetMs = timeOffset
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeZoneTextView(
    modifier: Modifier = Modifier,
    textSize: TextUnit,
    timeModel: TimeViewModel = hiltViewModel()
) {
    val state by timeModel.state.collectAsState()
    var systemTimeZoneId by remember { mutableStateOf(TimeZone.getDefault().id) }
    var expanded by remember { mutableStateOf(false) }

    // LaunchedEffect to monitor changes in the system time zone
    LaunchedEffect(Unit) {
        while (true) {
            systemTimeZoneId = TimeZone.getDefault().id
            kotlinx.coroutines.delay(1000L)
        }
    }

    val displayValue = when (state.timeZoneOption) {
        TimeSettingsStorage.TimeZoneOption.SYSTEM -> systemTimeZoneId
        TimeSettingsStorage.TimeZoneOption.LOCAL_MEAN_TIME -> stringResource(R.string.local_mean_time)
        TimeSettingsStorage.TimeZoneOption.LOCAL_SOLAR_TIME -> stringResource(R.string.local_solar_time)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Display the text with the current selection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
            ) {
                AppText(
                    text = displayValue,
                    fontSize = textSize,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { AppText(systemTimeZoneId) },
                    onClick = {
                        timeModel.onAction(
                            TimeAction.SetTimeZoneOption(
                                TimeSettingsStorage.TimeZoneOption.SYSTEM
                            )
                        )
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { AppText(stringResource(R.string.local_mean_time)) },
                    onClick = {
                        timeModel.onAction(
                            TimeAction.SetTimeZoneOption(
                                TimeSettingsStorage.TimeZoneOption.LOCAL_MEAN_TIME
                            )
                        )
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { AppText(stringResource(R.string.local_solar_time)) },
                    onClick = {
                        timeModel.onAction(
                            TimeAction.SetTimeZoneOption(
                                TimeSettingsStorage.TimeZoneOption.LOCAL_SOLAR_TIME
                            )
                        )
                        expanded = false
                    }
                )
            }
        }
        InfoButton(
            infoText = stringResource(id = R.string.time_zone_info),
            iconSize = 24.dp
        )
    }
}

@Composable
fun SendTimeButton(text: String, onClick: () -> Unit) {
    AppButton(
        onClick = {
            onClick()
        },
        text = text
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewLocalTimeCard() {
    LocalTimeView(
        Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(vertical = 0.dp) // Adjust padding as needed
    )
}