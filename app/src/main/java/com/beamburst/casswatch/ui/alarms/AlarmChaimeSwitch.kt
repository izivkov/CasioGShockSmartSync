package com.beamburst.casswatch.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.ui.common.AppCard
import com.beamburst.casswatch.ui.common.AppText
import AppSwitch

import androidx.compose.runtime.collectAsState

@Composable
fun AlarmChimeSwitch(
    modifier: Modifier = Modifier,
    onUpdate: (Boolean) -> Unit,
    alarmViewModel: AlarmViewModel = hiltViewModel(),
) {
    val alarms by alarmViewModel.alarms.collectAsState()
    var isChecked by remember { mutableStateOf(alarms.getOrNull(0)?.hasHourlyChime ?: false) }

    LaunchedEffect(alarms) {
        isChecked = alarms.getOrNull(0)?.hasHourlyChime ?: false
    }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(
                text = stringResource(id = R.string.signal_chime),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            AppSwitch(
                checked = isChecked,
                onCheckedChange = { checked ->
                    if (alarms.isNotEmpty()) {
                        isChecked = checked
                        alarmViewModel.toggleHourlyChime(checked)
                        onUpdate(checked)
                    }
                }
            )
        }
    }
}
