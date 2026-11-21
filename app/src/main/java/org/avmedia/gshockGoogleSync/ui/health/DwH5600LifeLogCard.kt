package org.avmedia.gshockGoogleSync.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockapi.model.StepCounterData
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NO_VALUE = "\u2013"
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault())

/**
 * The DW-H5600 daily values, as rows of compact tiles. Shown inside the Today card, under the gauge.
 */
@Composable
fun DwH5600DailyContent(
    data: StepCounterData,
    stepGoal: Int,
) {
    val health = data.h5600 ?: return
    val activity = health.activity
    val heart = health.heartRate

    StatRow(
        Stat(stringResource(id = R.string.energy_estimate), health.energyKcal?.let { "$it kcal" } ?: NO_VALUE),
        Stat(stringResource(id = R.string.active_time), activity?.let { formatDuration(it.activeSeconds) } ?: NO_VALUE),
        Stat(stringResource(id = R.string.goal), if (stepGoal > 0) format(stepGoal) else NO_VALUE),
    )
    StatRow(
        Stat(stringResource(id = R.string.walking), activity?.let { formatDuration(it.walkSeconds) } ?: NO_VALUE),
        Stat(stringResource(id = R.string.cycling), activity?.let { formatDuration(it.cycleSeconds) } ?: NO_VALUE),
        Stat(
            stringResource(id = R.string.heart_rate_day),
            if (heart?.maxBpm != null && heart.minBpm != null) "${heart.maxBpm} / ${heart.minBpm}" else NO_VALUE,
        ),
    )
    StatRow(
        Stat(stringResource(id = R.string.resting_heart_rate), heart?.restingBpm?.let { "$it bpm" } ?: NO_VALUE),
        Stat(
            stringResource(id = R.string.heart_rate_recent),
            health.recentHeartRate?.let { "${it.averageBpm} avg" } ?: NO_VALUE,
        ),
        Stat(stringResource(id = R.string.lifelog_status), pendingText(health.lifeLogPending)),
    )
    Text(
        text = stringResource(id = R.string.synced_at) + ": " + (data.timestamp?.format(TIME_FORMAT) ?: NO_VALUE),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The loading message while the watch's data is being read. */
@Composable
fun DwH5600Loading(status: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(id = R.string.loading_lifelog),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Red,
            )
            status?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A message from the last watch read, such as a failure to read the data. */
@Composable
fun DwH5600Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

private class Stat(val label: String, val value: String)

@Composable
private fun StatRow(vararg stats: Stat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        stats.forEach { stat ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stat.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
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
