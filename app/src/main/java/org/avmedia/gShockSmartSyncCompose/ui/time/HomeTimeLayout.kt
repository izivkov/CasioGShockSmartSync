package org.avmedia.gShockSmartSyncCompose.ui.time

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeTimeLayout(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    content: @Composable () -> Unit
) {
    if (isVisible) {
        Column(
            modifier = modifier
        ) {
            content()
        }
    }
}
