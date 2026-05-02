package com.beamburst.casswatch.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing

data class ButtonData(val text: String, val onClick: () -> Unit)

@Composable
fun ButtonsRow(
    buttons: List<ButtonData>,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    buttonSpacing: androidx.compose.ui.unit.Dp = Spacing.sm,
    rowPadding: androidx.compose.ui.unit.Dp = Spacing.sm
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = rowPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment
    ) {
        buttons.forEach { buttonData ->
            AppButton(
                text = buttonData.text,
                onClick = buttonData.onClick,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.xs, vertical = buttonSpacing)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewButtonRow() {
    val buttons = listOf(
        ButtonData(
            text = stringResource(
                id = R.string.send_alarms_to_phone
            ),
            onClick = { /* handle click */ }),
        ButtonData(
            text = stringResource(
                id = R.string.send_alarms_to_watch
            ),
            onClick = { /* handle click */ })
    )

    ButtonsRow(buttons = buttons)
}
