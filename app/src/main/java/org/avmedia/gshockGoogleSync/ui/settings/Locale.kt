package org.avmedia.gshockGoogleSync.ui.settings

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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockapi.WatchInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Locale(
    onUpdate: (SettingsViewModel.Locale) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val state by settingsViewModel.state.collectAsState()
    val localeSetting =
        state.settingsMap[SettingsViewModel.Locale::class.java] as SettingsViewModel.Locale

    var timeFormat by remember { mutableStateOf(localeSetting.timeFormat) }
    var dateFormat by remember { mutableStateOf(localeSetting.dateFormat) }

    LaunchedEffect(state.settings) {
        timeFormat = localeSetting.timeFormat
        dateFormat = localeSetting.dateFormat
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
                    text = stringResource(
                        id = R.string.time_format
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically // Aligns children vertically in the center
                ) {
                    RadioButton(
                        selected = timeFormat == SettingsViewModel.Locale.TimeFormat.TWELVE_HOURS,
                        onClick = {
                            timeFormat = SettingsViewModel.Locale.TimeFormat.TWELVE_HOURS
                            localeSetting.timeFormat = timeFormat
                            onUpdate(
                                localeSetting.copy(timeFormat = timeFormat)
                            )
                        }
                    )
                    AppTextLarge(
                        text = stringResource(
                            id = R.string._12h
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    RadioButton(
                        selected = timeFormat == SettingsViewModel.Locale.TimeFormat.TWENTY_FOUR_HOURS,
                        onClick = {
                            timeFormat = SettingsViewModel.Locale.TimeFormat.TWENTY_FOUR_HOURS
                            localeSetting.timeFormat = timeFormat
                            onUpdate(localeSetting.copy(timeFormat = timeFormat))
                        }
                    )
                    AppTextLarge(
                        text = stringResource(
                            id = R.string._24h
                        )
                    )
                }
            }

            if (WatchInfo.hasDateFormat) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppTextLarge(
                        text = stringResource(
                            id = R.string.date_format
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically // Aligns children vertically in the center
                    ) {
                        RadioButton(
                            selected = dateFormat == SettingsViewModel.Locale.DateFormat.MONTH_DAY,
                            onClick = {
                                dateFormat = SettingsViewModel.Locale.DateFormat.MONTH_DAY
                                localeSetting.dateFormat = dateFormat
                                onUpdate(localeSetting.copy(dateFormat = dateFormat))
                            }
                        )
                        AppTextLarge(
                            text = stringResource(
                                id = R.string.mm_dd
                            )
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        RadioButton(
                            selected = dateFormat == SettingsViewModel.Locale.DateFormat.DAY_MONTH,
                            onClick = {
                                dateFormat = SettingsViewModel.Locale.DateFormat.DAY_MONTH
                                localeSetting.dateFormat = dateFormat
                                onUpdate(localeSetting.copy(dateFormat = dateFormat))
                            }
                        )
                        AppTextLarge(
                            text = stringResource(
                                id = R.string.dd_mm
                            )
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                AppTextLarge(
                    text = stringResource(
                        id = R.string.language
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 0.dp)
                )

                LanguageDropdownMenu(
                    modifier = Modifier
                        .weight(1.5f)
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
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val state by settingsViewModel.state.collectAsState()

    val languages = SettingsViewModel.Locale.DayOfWeekLanguage.entries.map { it }
    var selectedLanguage by remember { mutableStateOf(localeSetting.dayOfWeekLanguage) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.settings) {
        selectedLanguage = localeSetting.dayOfWeekLanguage
    }

    // ExposedDropdownMenuBox wraps around the TextField and DropdownMenu
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLanguage.nativeName,
            onValueChange = {},
            readOnly = true,  // To prevent user from typing in the field
            label = {
                AppText(
                    text = stringResource(
                        R.string.select_language
                    )
                )
            },

            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
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
                    text = { AppText(language.nativeName) },
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

