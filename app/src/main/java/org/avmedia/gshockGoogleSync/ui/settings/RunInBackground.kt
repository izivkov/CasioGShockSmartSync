package org.avmedia.gshockGoogleSync.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R

@Composable
fun RunInBackground(
    onUpdate: (SettingsViewModel.RunInBackground) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val classType = SettingsViewModel.RunInBackground::class.java

    val settings by settingsViewModel.settings.collectAsState()
    val runInBackgroundSetting: SettingsViewModel.RunInBackground =
        settingsViewModel.getSetting(classType)
    var runInBackground by remember { mutableStateOf(runInBackgroundSetting.runInBackground) }

    LaunchedEffect(settings) {
        runInBackground = runInBackgroundSetting.runInBackground
    }

    val title = settingsViewModel.translateApi.stringResource(
        context = LocalContext.current,
        id = R.string.run_in_the_background
    )
    BasicSettings(title = title, isSwitchOn = runInBackground,
        onSwitchToggle = { newValue ->
            runInBackground = newValue // Update the state when the switch is toggled
            runInBackgroundSetting.runInBackground = newValue
            onUpdate(runInBackgroundSetting.copy(runInBackground = newValue))
        }
    )
}
