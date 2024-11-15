package org.avmedia.gShockSmartSyncCompose.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.R

@Composable
fun PowerSavings(
    onUpdate: (SettingsViewModel.PowerSavingMode) -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val classType = SettingsViewModel.PowerSavingMode::class.java

    val settings by settingsViewModel.settings.collectAsState()
    val powerSavingModeSetting: SettingsViewModel.PowerSavingMode =
        settingsViewModel.getSetting(classType)
    var powerSavingMode by remember { mutableStateOf(powerSavingModeSetting.powerSavingMode) }

    LaunchedEffect(settings) {
        powerSavingMode = powerSavingModeSetting.powerSavingMode
    }

    val title = stringResource(id = R.string.power_saving_mode)
    BasicSettings(title = title, isSwitchOn = powerSavingMode,
        onSwitchToggle = { newValue ->
            powerSavingMode = newValue // Update the state when the switch is toggled
            powerSavingModeSetting.powerSavingMode = newValue
            onUpdate(powerSavingModeSetting.copy(powerSavingMode = newValue))
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewPowerSavings() {
    PowerSavings(onUpdate = {})
}