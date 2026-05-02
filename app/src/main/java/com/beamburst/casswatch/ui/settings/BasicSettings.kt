package com.beamburst.casswatch.ui.settings

import AppSwitch
import com.beamburst.casswatch.ui.common.AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.beamburst.casswatch.theme.Spacing

@Composable
fun BasicSettings(
    title: String,
    isSwitchOn: Boolean,
    onSwitchToggle: (Boolean) -> Unit
) {
    SettingCard(modifier = Modifier.fillMaxWidth()) { contentPadding ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                AppTextLarge(
                    text = title,
                    modifier = Modifier.padding(end = Spacing.sm)
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
