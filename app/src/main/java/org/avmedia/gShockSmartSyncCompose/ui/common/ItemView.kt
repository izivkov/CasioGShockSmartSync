package org.avmedia.gShockSmartSyncCompose.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ItemView(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        content()
    }
}