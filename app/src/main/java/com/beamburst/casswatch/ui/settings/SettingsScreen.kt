package com.beamburst.casswatch.ui.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.CassiopeiaWatchTheme
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.ui.common.AppButton
import com.beamburst.casswatch.ui.common.AppSnackbar
import com.beamburst.casswatch.ui.common.AppText
import com.beamburst.casswatch.ui.common.ScreenTitle
import org.avmedia.gshockapi.WatchInfo

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SettingsScreen() {
    CassiopeiaWatchTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(
                    stringResource(id = R.string.settings),
                    Modifier
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    SettingsList()
                }

                BottomRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
            }
        }
    }
}

@Composable
fun SettingsList() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        settingsViewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    AppSnackbar(event.message)
                }
            }
        }
    }

    SettingsSectionTitle(stringResource(R.string.settings_section_locale))
    Locale(settingsViewModel::onSettingUpdated)

    SettingsSectionTitle(stringResource(R.string.settings_section_watch_behavior))
    OperationalTone(settingsViewModel::onSettingUpdated)
    Light(settingsViewModel::onSettingUpdated)
    if (WatchInfo.hasPowerSavingMode) {
        PowerSavings(settingsViewModel::onSettingUpdated)
    }
    if (WatchInfo.hasMultipleFonts) {
        Font(settingsViewModel::onSettingUpdated)
    }

    SettingsSectionTitle(stringResource(R.string.settings_section_sync))
    TimeAdjustment(settingsViewModel::onSettingUpdated)
}

@Composable
private fun SettingsSectionTitle(text: String) {
    AppText(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.md, bottom = Spacing.xxs)
    )
}

@Composable
fun BottomRow(modifier: Modifier, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = Spacing.xxs
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            AppButton(
                onClick = { settingsViewModel.sendToWatch() },
                text = stringResource(id = R.string.send_to_watch)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
        SettingsScreen()
}
