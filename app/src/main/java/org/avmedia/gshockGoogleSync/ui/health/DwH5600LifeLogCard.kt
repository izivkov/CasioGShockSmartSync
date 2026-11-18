package org.avmedia.gshockGoogleSync.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import AppTextLarge
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NO_VALUE = "\u2013"
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault())

/**
 * LifeLog data read from a DW-H5600: steps with the watch's goal, energy (an estimate), heart rate,
 * pending flags, and steps by hour. Shown only when the step data came from the DW-H5600.
 */
@Composable
fun DwH5600LifeLogCard(
    modifier: Modifier = Modifier,
    healthViewModel: HealthViewModel = hiltViewModel(),
) {
    val state by healthViewModel.state.collectAsState()
    val data = state.stepCounterData
    val health = data.h5600 ?: return

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTextLarge(
                text = stringResource(id = R.string.lifelog),
                modifier = Modifier.fillMaxWidth(),
            )

            InfoRow(
                label = stringResource(id = R.string.synced_at),
                value = data.timestamp?.format(TIME_FORMAT) ?: NO_VALUE,
            )

            HorizontalDivider()

            val steps = data.currentDaySteps
            InfoRow(
                label = stringResource(id = R.string.steps_today),
                value = steps?.let { String.format(Locale.US, "%,d", it) } ?: NO_VALUE,
            )
            InfoRow(
                label = stringResource(id = R.string.goal),
                value = if (state.stepGoal > 0 && steps != null) {
                    "${String.format(Locale.US, "%,d", state.stepGoal)} (${steps * 100 / state.stepGoal}%)"
                } else {
                    NO_VALUE
                },
            )
            InfoRow(
                label = stringResource(id = R.string.energy_estimate),
                value = health.energyKcal?.let { "$it kcal" } ?: NO_VALUE,
            )

            HorizontalDivider()

            val heart = health.heartRate
            InfoRow(
                label = stringResource(id = R.string.heart_rate_day),
                value = if (heart?.maxBpm != null && heart.minBpm != null) {
                    "${heart.maxBpm} / ${heart.minBpm} bpm"
                } else {
                    NO_VALUE
                },
            )
            InfoRow(
                label = stringResource(id = R.string.resting_heart_rate),
                value = heart?.restingBpm?.let { "$it bpm" } ?: NO_VALUE,
            )
            InfoRow(
                label = stringResource(id = R.string.heart_rate_recent),
                value = health.recentHeartRate?.let {
                    "${it.averageBpm} bpm avg (${it.minBpm}\u2013${it.maxBpm})"
                } ?: NO_VALUE,
            )

            HorizontalDivider()

            InfoRow(
                label = stringResource(id = R.string.lifelog_status),
                value = when (health.lifeLogPending) {
                    true -> stringResource(id = R.string.new_data_waiting)
                    false -> stringResource(id = R.string.up_to_date)
                    null -> NO_VALUE
                },
            )
            InfoRow(
                label = stringResource(id = R.string.workouts_status),
                value = when (health.activityLogPending) {
                    true -> stringResource(id = R.string.new_data_waiting)
                    false -> stringResource(id = R.string.up_to_date)
                    null -> NO_VALUE
                },
            )

            val hours = data.hourlySteps.withIndex().filter { (it.value ?: 0) > 0 }
            if (hours.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(id = R.string.steps_by_hour),
                    style = MaterialTheme.typography.titleSmall,
                )
                hours.forEach { (hour, count) ->
                    InfoRow(
                        label = String.format(Locale.US, "%02d:00", hour),
                        value = String.format(Locale.US, "%,d", count ?: 0),
                    )
                }
            }

            if (data.warnings.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(id = R.string.notes),
                    style = MaterialTheme.typography.titleSmall,
                )
                data.warnings.forEach { note ->
                    Text(text = note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
