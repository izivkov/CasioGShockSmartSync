package org.avmedia.gshockGoogleSync.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppTextLarge
import org.avmedia.gshockapi.io.H5600HealthIO
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NO_VALUE = "\u2013"
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault())

/**
 * LifeLog data read from a DW-H5600, grouped into cards: steps, energy and activity, heart rate, watch
 * status, steps by hour and notes. While the data is being read, a prominent loading message is shown.
 */
@Composable
fun DwH5600LifeLogCard(
    modifier: Modifier = Modifier,
    healthViewModel: HealthViewModel = hiltViewModel(),
) {
    val state by healthViewModel.state.collectAsState()
    val status by H5600HealthIO.status.collectAsState()
    val data = state.stepCounterData
    val health = data.h5600

    if (health == null) {
        val error = H5600HealthIO.lastError
        if (state.lifeLogLoading) {
            LoadingCard(status, modifier)
        } else if (error != null) {
            MessageCard(error, modifier)
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val steps = data.currentDaySteps
        val goal = state.stepGoal
        Section(title = stringResource(id = R.string.card_steps)) {
            InfoRow(
                label = stringResource(id = R.string.steps_today),
                value = steps?.let { format(it) } ?: NO_VALUE,
            )
            InfoRow(
                label = stringResource(id = R.string.goal),
                value = if (goal > 0 && steps != null) {
                    "${format(goal)} (${steps * 100 / goal}%)"
                } else {
                    NO_VALUE
                },
            )
        }

        Section(title = stringResource(id = R.string.card_activity)) {
            InfoRow(
                label = stringResource(id = R.string.energy_estimate),
                value = health.energyKcal?.let { "$it kcal" } ?: NO_VALUE,
            )
            health.activity?.let { activity ->
                InfoRow(
                    label = stringResource(id = R.string.walking),
                    value = formatDuration(activity.walkSeconds),
                )
                InfoRow(
                    label = stringResource(id = R.string.cycling),
                    value = formatDuration(activity.cycleSeconds),
                )
                InfoRow(
                    label = stringResource(id = R.string.active_time),
                    value = formatDuration(activity.activeSeconds),
                )
            }
        }

        Section(title = stringResource(id = R.string.card_heart_rate)) {
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
        }

        Section(title = stringResource(id = R.string.card_status)) {
            InfoRow(
                label = stringResource(id = R.string.synced_at),
                value = data.timestamp?.format(TIME_FORMAT) ?: NO_VALUE,
            )
            InfoRow(
                label = stringResource(id = R.string.lifelog_status),
                value = pendingText(health.lifeLogPending),
            )
            InfoRow(
                label = stringResource(id = R.string.workouts_status),
                value = pendingText(health.activityLogPending),
            )
        }

        val hours = data.hourlySteps.withIndex().filter { (it.value ?: 0) > 0 }
        if (hours.isNotEmpty()) {
            Section(title = stringResource(id = R.string.steps_by_hour)) {
                hours.forEach { (hour, count) ->
                    InfoRow(
                        label = String.format(Locale.US, "%02d:00", hour),
                        value = format(count ?: 0),
                    )
                }
            }
        }

        if (data.warnings.isNotEmpty()) {
            Section(title = stringResource(id = R.string.notes)) {
                data.warnings.forEach { note ->
                    Text(text = note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = {
                AppTextLarge(text = title, modifier = Modifier.fillMaxWidth())
                content()
            },
        )
    }
}

@Composable
private fun LoadingCard(
    status: String?,
    modifier: Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(id = R.string.loading_lifelog),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    textAlign = TextAlign.Start,
                )
                status?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    text: String,
    modifier: Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun format(value: Int): String = String.format(Locale.US, "%,d", value)

private fun formatDuration(seconds: Int): String =
    String.format(Locale.US, "%d:%02d", seconds / 3600, (seconds % 3600) / 60)

@Composable
private fun pendingText(pending: Boolean?): String =
    when (pending) {
        true -> stringResource(id = R.string.new_data_waiting)
        false -> stringResource(id = R.string.up_to_date)
        null -> NO_VALUE
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
