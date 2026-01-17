package org.avmedia.gshockGoogleSync.ui.settings

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
import org.avmedia.gshockapi.WatchInfo

@Composable
fun Font(
        onUpdate: (SettingsViewModel.Font) -> Unit,
        settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val state by settingsViewModel.state.collectAsState()
    if (state.settingsMap[SettingsViewModel.Font::class.java] == null) {
        print (">>>>>>>>>>>> Font setting is null")
        return
    }

    val fontSetting =
            state.settingsMap[SettingsViewModel.Font::class.java] as SettingsViewModel.Font

    var fontType by remember { mutableStateOf(fontSetting.font) }

    LaunchedEffect(state.settings) { fontType = fontSetting.font }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
        ) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppTextLarge(
                        text = stringResource(id = R.string.font),
                )

                Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                            selected = fontType == SettingsViewModel.Font.FontType.STANDARD,
                            onClick = {
                                fontType = SettingsViewModel.Font.FontType.STANDARD
                                onUpdate(fontSetting.copy(font = fontType))
                            },
                            modifier = Modifier.padding(end = 0.dp)
                    )
                    AppText(text = "Standard")

                    RadioButton(
                            selected = fontType == SettingsViewModel.Font.FontType.CLASSIC,
                            onClick = {
                                fontType = SettingsViewModel.Font.FontType.CLASSIC
                                onUpdate(fontSetting.copy(font = fontType))
                            },
                            modifier = Modifier.padding(end = 0.dp)
                    )
                    AppText(text = "Classic")
                }
            }
        }
    }
}
