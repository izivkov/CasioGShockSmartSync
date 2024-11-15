package org.avmedia.gShockSmartSyncCompose.ui.actions

import AppTextLarge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.avmedia.gShockSmartSyncCompose.R

@Composable
fun SeparatorView() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTextLarge(
            text = stringResource(id = R.string.emergency_actions),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSeparator() {
    SeparatorView()
}

