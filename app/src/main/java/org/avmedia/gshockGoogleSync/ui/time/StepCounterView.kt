package org.avmedia.gshockGoogleSync.ui.time

import AppTextLarge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard

@Composable
fun StepCounterView(
    modifier: Modifier = Modifier,
    timeViewModel: TimeViewModel = hiltViewModel()
) {
    val state by timeViewModel.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    AppCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextLarge(
                    text = stringResource(id = R.string.steps),
                    modifier = Modifier.weight(1f)
                )

                Box {
                    Row(
                        modifier = Modifier
                            .clickable { expanded = true }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = state.selectedStepDataOption.name)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        StepDataOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                onClick = {
                                    timeViewModel.onAction(TimeAction.SetStepDataOption(option))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (state.selectedStepDataOption) {
                StepDataOption.TODAY -> {
                    AppTextLarge(
                        text = (state.stepCounterData.currentDaySteps ?: 0).toString(),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                StepDataOption.HOURLY -> {
                    Column {
                        val hourly = state.stepCounterData.hourlySteps.filterNotNull().takeLast(5)
                        if (hourly.isEmpty()) {
                            Text("No history")
                        } else {
                            hourly.forEachIndexed { index, steps ->
                                Text("Last ${hourly.size - index}h: $steps steps")
                            }
                        }
                    }
                }

                StepDataOption.DAILY -> {
                    Column {
                        val daily = state.stepCounterData.dailyHistory.filterNotNull()
                        if (daily.isEmpty()) {
                            Text("No history")
                        } else {
                            daily.takeLast(7).forEachIndexed { index, steps ->
                                Text("Day ${daily.size - index}: $steps steps")
                            }
                        }
                    }
                }
            }
        }
    }
}
