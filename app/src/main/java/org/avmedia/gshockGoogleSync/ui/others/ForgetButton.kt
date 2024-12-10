package org.avmedia.gshockGoogleSync.ui.others

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.translateapi.DynamicResourceApi

@Composable
fun ForgetButton(
    modifier: Modifier,
    ptrConnectionViewModel: PreConnectionViewModel = hiltViewModel()
) {
    var isEnabled by remember { mutableStateOf(true) }

    fun listenForConnection() {
        val eventActions = arrayOf(
            EventAction("ConnectionStarted") {
                isEnabled = false
            },
            EventAction("WatchInitializationCompleted") {
                isEnabled = true
            },
            EventAction("ConnectionFailed") {
                isEnabled = true
            },
            EventAction("Disconnect") {
                isEnabled = true
            },
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    LaunchedEffect(Unit) {
        listenForConnection()
    }

    AppButton(
        enabled = isEnabled,
        onClick = {
            isEnabled = false
            ptrConnectionViewModel.forget()
        },
        text = DynamicResourceApi.getApi().stringResource(context = LocalContext.current, id = R.string.forget),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewForgetButton() {
    ForgetButton(
        modifier = Modifier
            .padding(start = 0.dp)
    )
}
