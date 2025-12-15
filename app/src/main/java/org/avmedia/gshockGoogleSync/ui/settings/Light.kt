package org.avmedia.gshockGoogleSync.ui.settings

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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.WatchInfo

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

    AppCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
        ) {
            if (WatchInfo.hasAutoLight) {
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
                AppTextLarge(
                    text = Utils.shortenString(
                        stringResource(id = R.string.illumination_period),
                        20
                    ),
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = lightDuration == SettingsViewModel.Light.LightDuration.TWO_SECONDS,
                        onClick = {
                            lightDuration = SettingsViewModel.Light.LightDuration.TWO_SECONDS
                            onUpdate(lightSetting.copy(duration = lightDuration))
                        },
                        modifier = Modifier.padding(end = 0.dp)
                    )
                    AppText(text = WatchInfo.shortLightDuration)

                    RadioButton(
                        selected = lightDuration == SettingsViewModel.Light.LightDuration.FOUR_SECONDS,
                        onClick = {
                            lightDuration = SettingsViewModel.Light.LightDuration.FOUR_SECONDS
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
