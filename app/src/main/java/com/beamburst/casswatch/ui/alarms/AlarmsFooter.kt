package com.beamburst.casswatch.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.ui.common.AppText
import java.util.concurrent.TimeUnit

@Composable
fun AlarmsFooter(
    modifier: Modifier = Modifier,
    alarmViewModel: AlarmViewModel
) {
    val lastSync by alarmViewModel.lastSync.collectAsState()
    val alarms by alarmViewModel.alarms.collectAsState()
    val firedAts by alarmViewModel.firedAts.collectAsState()

    val activeCount = alarms.indices.count { index ->
        alarms[index].enabled && !firedAts.containsKey(index)
    }
    val disabledCount = alarms.count { !it.enabled }

    val syncText = lastSync?.let { formatTimeAgo(it.syncedAt) }
        ?: stringResource(R.string.footer_never)

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AppText(
            text = stringResource(R.string.footer_last_sync, syncText),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppText(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppText(
            text = stringResource(R.string.footer_active, activeCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppText(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppText(
            text = stringResource(R.string.footer_disabled, disabledCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTimeAgo(syncedAt: Long): String {
    val diff = System.currentTimeMillis() - syncedAt
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "yesterday"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            .format(java.util.Date(syncedAt))
    }
}
