package com.beamburst.casswatch.ui.settings

import com.beamburst.casswatch.ui.common.AppText
import com.beamburst.casswatch.ui.common.AppTextLarge
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing
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

    SettingCard(modifier = Modifier.fillMaxWidth()) { contentPadding ->
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(contentPadding)
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
                            modifier = Modifier.padding(end = Spacing.xxs)
                    )
                    AppText(text = "Standard")

                    RadioButton(
                            selected = fontType == SettingsViewModel.Font.FontType.CLASSIC,
                            onClick = {
                                fontType = SettingsViewModel.Font.FontType.CLASSIC
                                onUpdate(fontSetting.copy(font = fontType))
                            },
                            modifier = Modifier.padding(end = Spacing.xxs)
                    )
                    AppText(text = "Classic")
                }
            }
        }
    }
}
