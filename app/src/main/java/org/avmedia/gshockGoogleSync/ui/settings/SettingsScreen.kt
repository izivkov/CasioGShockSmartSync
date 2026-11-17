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
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockGoogleSync.ui.common.WatchFeature
import org.avmedia.gshockGoogleSync.utils.Utils

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SettingsScreen() {
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
                    SettingsList()
                }
                // "Send to watch" removed: each change is applied right away (SettingsViewModel.scheduleApply).
            }
        }
    }
}

@Composable
fun SettingsList() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    Column {
        // DW-H5600 card moved to its own "Custom" tab.
        // Locale card removed: on the DW-H5600 it only held the time format, now on the Time tab.
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
        // Time adjustment card removed: on the DW-H5600 it only held Fine Adjustment, now on the Time tab.
        GreetingCard()
    }
}

@Composable
fun BottomRow(modifier: Modifier, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val isModified by settingsViewModel.isModified.collectAsState()
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
                // auto-fill help removed together with the AUTO button
            }

            // "AUTO" (auto-configure settings) button removed in this fork.
            val buttons = listOf(
                ButtonData(
                    text = stringResource(id = R.string.send_to_watch),
                    onClick = settingsViewModel::sendToWatch,
                    enabled = isModified
                )
            )

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
