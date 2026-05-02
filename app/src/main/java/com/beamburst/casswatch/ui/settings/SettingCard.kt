package com.beamburst.casswatch.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.ui.common.AppCard

@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
    content: @Composable (PaddingValues) -> Unit
) {
    AppCard(modifier = modifier, padding = Spacing.xs) {
        content(contentPadding)
    }
}
