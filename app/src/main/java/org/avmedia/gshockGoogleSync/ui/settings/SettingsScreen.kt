package org.avmedia.gshockGoogleSync.ui.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockGoogleSync.ui.common.WatchFeature
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchHealthLinks
import org.avmedia.gshockGoogleSync.ui.watchhealth.isH5600Watch
import org.avmedia.gshockGoogleSync.utils.Utils

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SettingsScreen(
    onOpenPlugin: (String) -> Unit = {},
    onOpenWatchFace: () -> Unit = {},
    onOpenWatchHealth: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenWorkouts: () -> Unit = {},
    onOpenTide: () -> Unit = {},
    onOpenAppLog: () -> Unit = {},
) {
    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(stringResource(id = R.string.settings), Modifier)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(0.dp)
                        .fillMaxWidth()
                ) {
                    // The plugins (Home Assistant, ...) have their one home here, first: switch and screen.
                    org.avmedia.gshockGoogleSync.plugins.PluginSettingsEntries(onOpen = onOpenPlugin)
                    SettingsList(onOpenWatchFace, onOpenWatchHealth, onOpenNotifications, onOpenWorkouts, onOpenTide)
                    AboutSection(onOpenAppLog)
                }
                BottomRow(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun SettingsList(
    onOpenWatchFace: () -> Unit = {},
    onOpenWatchHealth: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenWorkouts: () -> Unit = {},
    onOpenTide: () -> Unit = {},
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    Column {
        // The watch face and the health settings of the DW-H5600 family open from the top of Settings.
        if (isH5600Watch()) WatchHealthLinks(onOpenWatchFace, onOpenWatchHealth, onOpenNotifications, onOpenWorkouts, onOpenTide)
        WatchFeature(id = "locale_card") {
            Locale(settingsViewModel::onSettingUpdated)
        }
        WatchFeature(id = "operation_tone_card") {
            OperationalTone(settingsViewModel::onSettingUpdated)
        }
        WatchFeature(id = "light_card") {
            Light(settingsViewModel::onSettingUpdated)
        }
        WatchFeature(id = "power_saving_card") {
            PowerSavings(settingsViewModel::onSettingUpdated)
        }
        WatchFeature(id = "font_card") {
            Font(settingsViewModel::onSettingUpdated)
        }
        WatchFeature(id = "time_adjustment_card") {
            TimeAdjustment(settingsViewModel::onSettingUpdated)
        }
        PhoneNotificationsCard()
        if (isH5600Watch()) BackgroundReadCard()
    }
}

@Composable
fun BottomRow(modifier: Modifier, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val isModified by settingsViewModel.isModified.collectAsState()
    // AUTO fills in basic watch settings (language, formats, light...), so it is only offered
    // on watches that have them.
    val hasWatchSettings = LocalWatchFeatureManager.current.isFeatureSupported("settings.watch_settings")
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (hasWatchSettings) {
                    InfoButton(infoText = stringResource(id = R.string.auto_fill_help))
                }
            }

            val autoButton = ButtonData(
                text = Utils.shortenStringNewLine(
                    stringResource(id = R.string.auto_configure_settings),
                    15,
                    "AUTO"
                ),
                onClick = settingsViewModel::setSmartDefaults
            )
            val sendButton = ButtonData(
                text = stringResource(id = R.string.send_to_watch),
                onClick = settingsViewModel::sendToWatch,
                enabled = isModified
            )
            val buttons = if (hasWatchSettings) listOf(autoButton, sendButton) else listOf(sendButton)

            ButtonsRow(buttons = buttons, modifier = Modifier.weight(2.5f))
            Spacer(modifier = Modifier.weight(.5f))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
    SettingsScreen()
}

/** The bottom of Settings: the app's and the library's version, and the App log (its screen has Share). */
@Composable
fun AboutSection(onOpenAppLog: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        androidx.compose.material3.Text(
            "About", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        org.avmedia.gshockGoogleSync.ui.watchhealth.ValueRow(title = "App version", value = org.avmedia.gshockGoogleSync.BuildConfig.VERSION_NAME)
        org.avmedia.gshockGoogleSync.ui.watchhealth.ValueRow(title = "Library version", value = org.avmedia.gshockGoogleSync.BuildConfig.LIBRARY_VERSION)
        org.avmedia.gshockGoogleSync.ui.watchhealth.ValueRow(title = "App log", value = ">", onClick = onOpenAppLog)
    }
}
