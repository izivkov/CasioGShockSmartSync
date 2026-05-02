package com.beamburst.casswatch.ui.common

import AppSwitch
import com.beamburst.casswatch.ui.common.AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            .padding(8.dp)
            .wrapContentWidth()
    ) {
        AppText(
            text = text,
            fontSize = 22.sp,
            modifier = Modifier
                .padding(end = 8.dp)
                .wrapContentWidth()
        )
        AppSwitch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = modifier
        )
    }
}
