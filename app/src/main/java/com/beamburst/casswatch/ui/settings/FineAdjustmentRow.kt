package com.beamburst.casswatch.ui.settings

import com.beamburst.casswatch.ui.common.AppTextLink
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.ui.common.InfoButton
import com.beamburst.casswatch.ui.common.ValueSelectionDialog

@Composable
fun FineAdjustmentRow(
    modifier: Modifier = Modifier,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLabel(
            text = stringResource(id = R.string.fine_adjustment),
            modifier = Modifier.padding(end = Spacing.sm)
        )
        InfoButton(
            infoText = stringResource(id = R.string.fine_adjustment_info)
        )

        Spacer(modifier = Modifier.weight(1f))

        var showDialog by remember { mutableStateOf(false) }

        AppTextLink(
            text = "$value ms",
            modifier = Modifier
                .clickable { showDialog = true }
                .padding(Spacing.sm),
        )

        if (showDialog) {
            ValueSelectionDialog(
                initialValue = value,
                range = -10000..10000,
                step = 100,
                onDismiss = { showDialog = false },
                onConfirm = { newValue ->
                    showDialog = false
                    onValueChange(newValue)
                },
                title = stringResource(R.string.fine_adjustment),
                label = stringResource(R.string.ms_between_10000_and_10000),
                unit = " ms"
            )
        }
    }
}
