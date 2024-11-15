package org.avmedia.gShockSmartSyncCompose.ui.time

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.avmedia.gShockSmartSyncCompose.ui.common.AppCard

@Composable
fun WatchInfoCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    elevation: Dp = 1.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    AppCard(
        modifier = modifier,
    ) {
        content()
    }
}
