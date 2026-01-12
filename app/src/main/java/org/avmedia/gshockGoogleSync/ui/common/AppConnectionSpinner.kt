package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

@Composable
fun AppConnectionSpinner(modifier: Modifier = Modifier) {
    var isVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val name = "AppConnectionSpinner-${java.util.UUID.randomUUID()}"
        var isInitial = true

        val eventActions =
                arrayOf(
                        EventAction("ConnectionStarted") {
                            if (!isInitial) {
                                isVisible = true
                            }
                        },
                        EventAction("WatchInitializationCompleted") { isVisible = false },
                        EventAction("ConnectionFailed") { isVisible = false },
                        EventAction("Disconnect") { isVisible = false },
                        EventAction("ApiError") { isVisible = false }
                )
        ProgressEvents.runEventActions(name, eventActions)

        // Post to handler to ensure we clear the flag AFTER any potential synchronous
        // or immediately-posted sticky events are processed.
        android.os.Handler(android.os.Looper.getMainLooper()).post { isInitial = false }

        onDispose { ProgressEvents.subscriber.stop(name) }
    }

    if (isVisible) {
        AppCard(
                modifier = modifier,
                padding = 8.dp,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                elevation = 0.dp
        ) {
            androidx.compose.foundation.layout.Box(
                    contentAlignment = androidx.compose.ui.Alignment.Center
            ) { CircularProgressIndicator() }
        }
    }
}
