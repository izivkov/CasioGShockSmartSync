package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import AppTextLink
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockGoogleSync.ui.common.ValueSelectionDialog

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
        AppText(
            text = stringResource(id = R.string.fine_adjustment),
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 6.dp)
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
                .padding(6.dp),
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
