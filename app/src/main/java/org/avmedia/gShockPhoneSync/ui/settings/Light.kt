package org.avmedia.gShockPhoneSync.ui.settings

import AppSwitch
import AppText
import AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSync.R
import org.avmedia.gShockPhoneSync.ui.common.AppCard
import org.avmedia.gshockapi.WatchInfo

@Composable
fun Light(
    onUpdate: (SettingsViewModel.Light) -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val classType = SettingsViewModel.Light::class.java

    val settings by settingsViewModel.settings.collectAsState()
    val lightSetting: SettingsViewModel.Light = settingsViewModel.getSetting(classType)

    var autoLight by remember { mutableStateOf(lightSetting.autoLight) }
    var lightDuration by remember { mutableStateOf(lightSetting.duration) }

    LaunchedEffect(settings, autoLight, lightDuration) {
        autoLight = lightSetting.autoLight
        lightDuration = lightSetting.duration
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
        ) {
            // Auto Light Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    AppTextLarge(
                        text = stringResource(id = R.string.auto_light),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                AppSwitch(
                    checked = autoLight,
                    onCheckedChange = {
                        autoLight = it
                        lightSetting.autoLight = it
                        onUpdate(lightSetting.copy(autoLight = it))
                    }
                )
            }

            // Illumination Period Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppTextLarge(
                    text = "Illumination Period",
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = lightDuration == SettingsViewModel.Light.LIGHT_DURATION.TWO_SECONDS,
                        onClick = {
                            lightDuration = SettingsViewModel.Light.LIGHT_DURATION.TWO_SECONDS
                            lightSetting.duration =
                                SettingsViewModel.Light.LIGHT_DURATION.TWO_SECONDS
                            onUpdate(lightSetting.copy(duration = lightDuration))
                        },
                        modifier = Modifier.padding(end = 0.dp)
                    )
                    AppText(text = WatchInfo.shortLightDuration)

                    RadioButton(
                        selected = lightDuration == SettingsViewModel.Light.LIGHT_DURATION.FOUR_SECONDS,
                        onClick = {
                            lightDuration = SettingsViewModel.Light.LIGHT_DURATION.FOUR_SECONDS
                            lightSetting.duration =
                                SettingsViewModel.Light.LIGHT_DURATION.FOUR_SECONDS
                            onUpdate(lightSetting.copy(duration = lightDuration))
                        },
                        modifier = Modifier.padding(end = 0.dp)
                    )
                    AppText(text = WatchInfo.longLightDuration)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "SettingsItem Preview")
@Composable
fun PreviewSettingsItem() {
    Light(
        onUpdate = { updatedSetting ->
            println("Setting changed: $updatedSetting")
        }
    )
}

