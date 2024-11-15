package org.avmedia.gShockSmartSyncCompose.ui.others

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.ui.common.AppButton
import org.avmedia.gShockSmartSyncCompose.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

@Composable
fun ForgetButton(
    modifier: Modifier,
    ptrConnectionViewModel: PreConnectionViewModel = PreConnectionViewModel()
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
        text = stringResource(id = R.string.forget),
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
