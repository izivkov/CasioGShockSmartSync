package org.avmedia.gShockSmartSyncCompose.ui.settings

import AppText
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.ui.common.AppTextField
import org.avmedia.gShockSmartSyncCompose.ui.common.InfoButton

@Composable
fun FineAdjustmentRow(
    modifier: Modifier = Modifier,
    onUpdate: (SettingsViewModel.TimeAdjustment) -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
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
            text = stringResource(R.string.fine_time_adjustment),
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 6.dp)
        )
        InfoButton(infoText = stringResource(R.string.fine_adjustment_info))

        Spacer(modifier = Modifier.weight(1f))

        val pattern = remember { Regex("^(0|(-?[1-9][0-9]{0,2}|-?1000|-?[1-4][0-9]{3}|-?5000))$") }
        AppTextField(
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .padding(end = 12.dp, start = 12.dp, top = 0.dp, bottom = 0.dp)
                .weight(2f),
            value = text,
            onValueChange = { newText: String ->
                if (newText.isEmpty() || newText == "-" || newText.matches(pattern)) {
                    text = newText
                    onUpdate(timeAdjustmentSetting.copy(fineAdjustment = text.toIntOrNull() ?: 0))
                }
            },
            placeholderText = "0000",
        )
    }
}


