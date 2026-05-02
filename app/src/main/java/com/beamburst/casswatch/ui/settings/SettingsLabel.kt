package com.beamburst.casswatch.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.beamburst.casswatch.ui.common.AppText

@Composable
fun SettingsLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    AppText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium
    )
}
