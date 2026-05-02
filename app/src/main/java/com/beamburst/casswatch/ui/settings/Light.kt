package com.beamburst.casswatch.ui.settings

import AppSwitch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing
import org.avmedia.gshockapi.WatchInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Light(
    onUpdate: (SettingsViewModel.Light) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val state by settingsViewModel.state.collectAsState()
    val lightSetting =
        state.settingsMap[SettingsViewModel.Light::class.java] as SettingsViewModel.Light

    var autoLight by remember { mutableStateOf(lightSetting.autoLight) }
    var lightDuration by remember { mutableStateOf(lightSetting.duration) }

    LaunchedEffect(state.settings) {
        autoLight = lightSetting.autoLight
        lightDuration = lightSetting.duration
    }

    SettingCard(modifier = Modifier.fillMaxWidth()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            if (WatchInfo.hasAutoLight) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        SettingsLabel(
                            text = stringResource(id = R.string.auto_light),
                            modifier = Modifier.padding(end = Spacing.sm)
                        )
                    }
                    AppSwitch(
                        checked = autoLight,
                        onCheckedChange = {
                            autoLight = it
                            onUpdate(lightSetting.copy(autoLight = it))
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SettingsLabel(
                    text = stringResource(id = R.string.illumination_period),
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                        selected = lightDuration == SettingsViewModel.Light.LightDuration.ONE_POINT_FIVE_SECONDS,
                        onClick = {
                            lightDuration = SettingsViewModel.Light.LightDuration.ONE_POINT_FIVE_SECONDS
                            onUpdate(lightSetting.copy(duration = lightDuration))
                        },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text(SettingsViewModel.Light.LightDuration.ONE_POINT_FIVE_SECONDS.value) }
                        )
                        SegmentedButton(
                            selected = lightDuration == SettingsViewModel.Light.LightDuration.THREE_SECONDS,
                            onClick = {
                                lightDuration = SettingsViewModel.Light.LightDuration.THREE_SECONDS
                                onUpdate(lightSetting.copy(duration = lightDuration))
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text(SettingsViewModel.Light.LightDuration.THREE_SECONDS.value) }
                        )
                    }
                }
            }
        }
    }
}
