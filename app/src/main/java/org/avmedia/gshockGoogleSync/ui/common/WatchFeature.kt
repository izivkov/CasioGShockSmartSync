package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

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

