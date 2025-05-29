package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import AppTextLink
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockGoogleSync.ui.common.ValueSelectionDialog

@Composable
fun FineAdjustmentRow(
    modifier: Modifier = Modifier,
    onUpdate: (SettingsViewModel.TimeAdjustment) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val classType = SettingsViewModel.TimeAdjustment::class.java
    val settings by settingsViewModel.settings.collectAsState()
    val timeAdjustmentSetting: SettingsViewModel.TimeAdjustment =
        settingsViewModel.getSetting(classType)

    var fineAdjustment by remember { mutableIntStateOf(timeAdjustmentSetting.fineAdjustment) }
    var text by remember {
        mutableStateOf(fineAdjustment.toString())
    }

    LaunchedEffect(settings) {
        fineAdjustment = timeAdjustmentSetting.fineAdjustment
        text = fineAdjustment.toString()
        onUpdate(timeAdjustmentSetting.copy(fineAdjustment = fineAdjustment))
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = stringResource(
                R.string.fine_time_adjustment
            ),
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 2.dp)
        )
        InfoButton(
            infoText = stringResource(
                R.string.fine_adjustment_info
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        var showDialog by remember { mutableStateOf(false) }
        var selectedValue by remember { mutableIntStateOf(text.toInt()) }

        AppTextLink(
            text = "$selectedValue ms",
            modifier = Modifier
                .clickable { showDialog = true }
                .padding(2.dp),
        )
        if (showDialog) {
            ValueSelectionDialog(
                initialValue = selectedValue,
                range = -10000..10000,
                step = 100,
                title = stringResource(
                    R.string.fine_adjustment
                ),
                label = stringResource(
                    R.string.ms_between_10000_and_10000
                ),
                onDismiss = { showDialog = false },
                onConfirm = { newValue ->
                    selectedValue = newValue
                    showDialog = false
                    onUpdate(timeAdjustmentSetting.copy(fineAdjustment = newValue))
                },
                unit = "ms",
            )
        }
    }
}


