package org.avmedia.gShockSmartSyncCompose.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun <T> ItemList(
    items: List<T>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        items.forEach { item ->
            ItemView {}
        }
    }
}