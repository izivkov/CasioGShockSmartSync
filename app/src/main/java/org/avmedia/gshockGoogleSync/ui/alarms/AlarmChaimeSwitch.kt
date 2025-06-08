package org.avmedia.gshockGoogleSync.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppSwitchWithText

@Composable
fun AlarmChimeSwitch(
    modifier: Modifier = Modifier,
    onUpdate: (Boolean) -> Unit,
    alarmViewModel: AlarmViewModel = hiltViewModel(),
) {
    val alarms = alarmViewModel.alarms
    var isChecked by remember { mutableStateOf(alarms.getOrNull(0)?.hasHourlyChime ?: false) }

    LaunchedEffect(alarms) {
        isChecked = alarms.getOrNull(0)?.hasHourlyChime ?: false
    }

    Column(
        modifier = Modifier
            .padding(0.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.End,
    ) {
        AppSwitchWithText(
            isChecked = isChecked,
            onCheckedChange = { checked ->
                if (alarms.isNotEmpty()) {
                    isChecked = checked
                    alarmViewModel.toggleHourlyChime(checked)
                    onUpdate(checked)
                }
            },
            modifier = modifier,
            text = stringResource(id = R.string.signal_chime)
        )
    }
}
