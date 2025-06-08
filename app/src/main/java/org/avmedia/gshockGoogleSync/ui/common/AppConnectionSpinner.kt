package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

@Composable
fun AppConnectionSpinner(
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    AppCard(
        modifier = modifier,
        padding = 8.dp
    ) {
        val eventActions = arrayOf(
            EventAction("ConnectionStarted") {
                isVisible = true
            },
            EventAction("WatchInitializationCompleted") {
                isVisible = false
            }
        )
        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)

        if (isVisible) {
            CircularProgressIndicator()
        }
    }
}
