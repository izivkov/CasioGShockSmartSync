package org.avmedia.gShockSmartSyncCompose.ui.common

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.avmedia.gShockSmartSyncCompose.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

@Composable
fun AppConnectionSpinner(
    modifier: Modifier = Modifier
) {
    var showIt by remember { mutableStateOf(false) }

    val eventActions = arrayOf(
        EventAction("ConnectionStarted") {
            showIt = true
        },
        EventAction("WatchInitializationCompleted") {
            showIt = false
        },
    )
    ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)

    if (showIt) {
        CircularProgressIndicator(
            modifier = modifier
        )
    }
}
