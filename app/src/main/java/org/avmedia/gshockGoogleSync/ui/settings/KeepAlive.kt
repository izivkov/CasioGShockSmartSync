package org.avmedia.gshockGoogleSync.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.services.KeepAliveManager

@Composable
fun KeepAlive(
    onUpdate: (SettingsViewModel.KeepAlive) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keepAliveManager = remember(context) { KeepAliveManager.getInstance(context) }
    val state by settingsViewModel.state.collectAsState()

    // Safe casting with elvis operator
    val keepAliveSetting =
        (state.settingsMap[SettingsViewModel.KeepAlive::class.java] as? SettingsViewModel.KeepAlive)
            ?: SettingsViewModel.KeepAlive(context) // Provide default value

    var keepAlive by remember { mutableStateOf(keepAliveSetting.keepAlive) }

    LaunchedEffect(state.settings) {
        keepAlive = keepAliveSetting.keepAlive
    }

    val title = stringResource(
        id = R.string.keep_alive
    )

    BasicSettings(
        title = title,
        isSwitchOn = keepAlive,
        onSwitchToggle = { newValue ->
            keepAlive = newValue
            keepAliveSetting.keepAlive = newValue
            if (newValue) keepAliveManager.enable() else keepAliveManager.disable()
            onUpdate(keepAliveSetting.copy(keepAlive = newValue))
        }
    )
}
