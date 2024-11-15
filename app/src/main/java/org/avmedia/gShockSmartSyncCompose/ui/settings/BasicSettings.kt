package org.avmedia.gShockSmartSyncCompose.ui.settings

import AppSwitch
import AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.avmedia.gShockSmartSyncCompose.ui.common.AppCard

@Composable
fun BasicSettings(
    title: String,
    isSwitchOn: Boolean,
    onSwitchToggle: (Boolean) -> Unit
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                AppTextLarge(
                    text = title,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Switch to toggle the option
            AppSwitch(
                checked = isSwitchOn,
                onCheckedChange = onSwitchToggle
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewBasicSettings() {
    BasicSettings(
        title = "Basic Settings",
        isSwitchOn = true,
        onSwitchToggle = {})
}