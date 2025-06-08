package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
fun PopupMessageReceiver(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    duration: SnackbarDuration = SnackbarDuration.Short
) {
    // Store reference to hostState in controller
    DisposableEffect(snackbarHostState) {
        SnackbarController.snackbarHostState = snackbarHostState
        onDispose {
            SnackbarController.snackbarHostState = null
        }
    }

    LaunchedEffect(Unit) {
        val eventActions = arrayOf(
            EventAction("SnackbarMessage") {
                val message = ProgressEvents.getPayload("SnackbarMessage") as String
                launch {
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = duration
                    )
                }
            }
        )
        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(16.dp)
        )
    }
}
