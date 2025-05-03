package org.avmedia.gshockGoogleSync.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.services.KeepAliveManager

@Composable
fun KeepAlive(
    onUpdate: (SettingsViewModel.KeepAlive) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keepAliveManager = remember(context) { KeepAliveManager.getInstance(context) }
    val settings by settingsViewModel.settings.collectAsState()

    val keepAliveSetting = settingsViewModel.getSetting(SettingsViewModel.KeepAlive::class.java)
    var keepAlive by remember { mutableStateOf(keepAliveSetting.keepAlive) }

    LaunchedEffect(settings) {
        keepAlive = keepAliveSetting.keepAlive
    }

    val title = settingsViewModel.translateApi.stringResource(
        context = context,
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