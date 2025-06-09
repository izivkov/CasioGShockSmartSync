package org.avmedia.gshockGoogleSync.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R

@Composable
fun PowerSavings(
    onUpdate: (SettingsViewModel.PowerSavingMode) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val state by settingsViewModel.state.collectAsState()
    val powerSavingModeSetting = state.settingsMap[SettingsViewModel.PowerSavingMode::class.java] as SettingsViewModel.PowerSavingMode

    var powerSavingMode by remember { mutableStateOf(powerSavingModeSetting.powerSavingMode) }

    LaunchedEffect(state.settings) {
        powerSavingMode = powerSavingModeSetting.powerSavingMode
    }

    val title = stringResource(id = R.string.power_saving_mode)

    BasicSettings(
        title = title,
        isSwitchOn = powerSavingMode,
        onSwitchToggle = { newValue ->
            powerSavingMode = newValue
            onUpdate(powerSavingModeSetting.copy(powerSavingMode = newValue))
        }
    )
}
