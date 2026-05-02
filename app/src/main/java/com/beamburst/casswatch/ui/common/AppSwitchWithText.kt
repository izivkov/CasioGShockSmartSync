package com.beamburst.casswatch.ui.common

import AppSwitch
import com.beamburst.casswatch.ui.common.AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.beamburst.casswatch.theme.Spacing

@Composable
fun AppSwitchWithText(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .padding(Spacing.sm)
            .wrapContentWidth()
    ) {
        AppText(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(end = Spacing.sm)
                .wrapContentWidth()
        )
        AppSwitch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = modifier
        )
    }
}
