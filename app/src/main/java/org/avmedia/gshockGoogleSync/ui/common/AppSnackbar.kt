package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

object SnackbarController {
    var snackbarHostState: SnackbarHostState? = null
}

fun AppSnackbar(message: String) {
    ProgressEvents.onNext("SnackbarMessage", message)
}

@Composable
fun PopupMessageReceiver(duration: SnackbarDuration = SnackbarDuration.Short) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SnackbarController.snackbarHostState = snackbarHostState
        val eventActions =
                arrayOf(
                        EventAction("SnackbarMessage") {
                            scope.launch {
                                val message = ProgressEvents.getPayload("SnackbarMessage") as String
                                snackbarHostState.showSnackbar(
                                        message = message,
                                        duration = duration
                                )
                            }
                        },
                )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    Box(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            contentAlignment = Alignment.BottomCenter, // Align the popup at the bottom
    ) { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(16.dp)) }
}
