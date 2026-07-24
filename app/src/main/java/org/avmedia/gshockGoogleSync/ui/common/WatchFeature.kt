package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import AppTextLarge

val LocalWatchFeatureManager = staticCompositionLocalOf<IWatchFeatureManager> {
    error("No WatchFeatureManager provided")
}

@Composable
fun WatchFeature(id: String, content: @Composable () -> Unit) {
    val manager = LocalWatchFeatureManager.current
    if (manager.isFeatureSupported(id)) {
        content()
    }
}

@Composable
fun WatchAppCard(
    id: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val manager = LocalWatchFeatureManager.current
    if (manager.isCardSupported(id)) {
        AppCard(modifier = modifier) {
            content()
        }
    } else {
        AppCard(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                AppTextLarge(text = "N/A")
            }
        }
    }
}
