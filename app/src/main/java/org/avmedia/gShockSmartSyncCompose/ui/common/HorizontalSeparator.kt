package org.avmedia.gShockSmartSyncCompose.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun HorizontalSeparator(width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxWidth()
    )
}