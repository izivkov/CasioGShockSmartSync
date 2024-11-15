package org.avmedia.gShockSmartSyncCompose.ui.settings

import AppText
import AppTextLarge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.ui.common.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Locale(
    onUpdate: (SettingsViewModel.Locale) -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val classType = SettingsViewModel.Locale::class.java
    val settings by settingsViewModel.settings.collectAsState()
    val localeSetting: SettingsViewModel.Locale = settingsViewModel.getSetting(classType)

    var timeFormat by remember { mutableStateOf(localeSetting.timeFormat) }
    var dateFormat by remember { mutableStateOf(localeSetting.dateFormat) }
    var selectedLanguage by remember { mutableStateOf(localeSetting.dayOfWeekLanguage) }

    LaunchedEffect(settings, timeFormat, dateFormat, selectedLanguage) {
        timeFormat = localeSetting.timeFormat
        dateFormat = localeSetting.dateFormat
        selectedLanguage = localeSetting.dayOfWeekLanguage
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
        ) {
            // Time Format Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppTextLarge(
                    text = stringResource(id = R.string.time_format),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically // Aligns children vertically in the center
                ) {
                    RadioButton(
                        selected = timeFormat == SettingsViewModel.Locale.TIME_FORMAT.TWELVE_HOURS,
                        onClick = {
                            timeFormat = SettingsViewModel.Locale.TIME_FORMAT.TWELVE_HOURS
                            localeSetting.timeFormat = timeFormat
                            onUpdate(
                                localeSetting.copy(timeFormat = timeFormat)
                            )
                        }
                    )
                    AppTextLarge(text = stringResource(id = R.string._12h))
                    Spacer(modifier = Modifier.width(10.dp))
                    RadioButton(
                        selected = timeFormat == SettingsViewModel.Locale.TIME_FORMAT.TWENTY_FOUR_HOURS,
                        onClick = {
                            timeFormat = SettingsViewModel.Locale.TIME_FORMAT.TWENTY_FOUR_HOURS
                            localeSetting.timeFormat = timeFormat
                            onUpdate(localeSetting.copy(timeFormat = timeFormat))
                        }
                    )
                    AppTextLarge(text = stringResource(id = R.string._24h))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.date_format),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically // Aligns children vertically in the center
                ) {
                    RadioButton(
                        selected = dateFormat == SettingsViewModel.Locale.DATE_FORMAT.MONTH_DAY,
                        onClick = {
                            dateFormat = SettingsViewModel.Locale.DATE_FORMAT.MONTH_DAY
                            localeSetting.dateFormat = dateFormat
                            onUpdate(localeSetting.copy(dateFormat = dateFormat))
                        }
                    )
                    AppText(text = stringResource(id = R.string.mm_dd))

                    Spacer(modifier = Modifier.width(10.dp))

                    RadioButton(
                        selected = dateFormat == SettingsViewModel.Locale.DATE_FORMAT.DAY_MONTH,
                        onClick = {
                            dateFormat = SettingsViewModel.Locale.DATE_FORMAT.DAY_MONTH
                            localeSetting.dateFormat = dateFormat
                            onUpdate(localeSetting.copy(dateFormat = dateFormat))
                        }
                    )
                    AppText(text = stringResource(id = R.string.dd_mm))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                AppText(
                    text = stringResource(id = R.string.language),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 0.dp)
                )

                LanguageDropdownMenu(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 2.dp),
                    onUpdate = onUpdate,
                    localeSetting = localeSetting,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdownMenu(
    onUpdate: (SettingsViewModel.Locale) -> Unit,
    localeSetting: SettingsViewModel.Locale,
    modifier: Modifier,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val settings by settingsViewModel.settings.collectAsState()

    val languages = SettingsViewModel.Locale.DAY_OF_WEEK_LANGUAGE.entries.map { it }
    var selectedLanguage by remember { mutableStateOf(localeSetting.dayOfWeekLanguage) }
    var expanded by remember { mutableStateOf(false) }  // State to control menu visibility

    LaunchedEffect(settings) {
        selectedLanguage = localeSetting.dayOfWeekLanguage
    }

    // ExposedDropdownMenuBox wraps around the TextField and DropdownMenu
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLanguage.value,
            onValueChange = {},
            readOnly = true,  // To prevent user from typing in the field
            label = { AppText(text = "Select a language") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    MenuAnchorType.PrimaryNotEditable,
                    true
                ),  // Attach menu to the OutlinedTextField
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { AppText(language.value) },
                    onClick = {
                        selectedLanguage = language
                        expanded = false
                        onUpdate(localeSetting.copy(dayOfWeekLanguage = language))
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLocale() {
    Locale(onUpdate = {})
}

