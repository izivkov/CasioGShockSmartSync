package com.beamburst.casswatch.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamburst.casswatch.theme.Spacing

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = Spacing.xs,
    elevation: Dp = 1.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.padding(padding),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(elevation),
        border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null
    ) {
        content()
    }
}
