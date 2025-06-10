package org.avmedia.gshockGoogleSync.ui.settings

import AppSwitch
import AppText
import AppTextLarge
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockapi.WatchInfo

@Composable
fun OperationalTone(
    onUpdate: (SettingsViewModel.OperationSound) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val state by settingsViewModel.state.collectAsState()
    val operationToneSetting =
        state.settingsMap[SettingsViewModel.OperationSound::class.java] as SettingsViewModel.OperationSound

    var sound by remember { mutableStateOf(operationToneSetting.sound) }
    var vibrate by remember { mutableStateOf(operationToneSetting.vibrate) }

    LaunchedEffect(state.settings) {
        sound = operationToneSetting.sound
        vibrate = operationToneSetting.vibrate
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextLarge(
                    text = stringResource(id = R.string.operational_sound),
                    modifier = Modifier.padding(end = 6.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                AppSwitch(
                    checked = sound,
                    onCheckedChange = { newValue ->
                        sound = newValue
                        onUpdate(operationToneSetting.copy(sound = newValue))
                    },
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 12.dp)
                )
            }

            if (WatchInfo.vibrate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            text = "Vibrate",
                            modifier = Modifier.wrapContentWidth(),
                        )
                        Checkbox(
                            checked = vibrate,
                            onCheckedChange = { newValue ->
                                vibrate = newValue
                                onUpdate(operationToneSetting.copy(vibrate = newValue))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
