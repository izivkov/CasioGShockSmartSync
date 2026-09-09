package org.avmedia.gshockGoogleSync.ui.time

import AppText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard

@Composable
fun WatchNameView(
    modifier: Modifier = Modifier,
    timeModel: TimeViewModel = hiltViewModel()
) {
    val state by timeModel.state.collectAsState()

    AppCard(
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            WatchName(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                text = state.watchName
            )

            if (state.isVoiceCommandSupported) {
                AppCard(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Verbose",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Switch(
                            checked = state.isVoiceVerbose,
                            onCheckedChange = { timeModel.onAction(TimeAction.SetVoiceVerbose(it)) },
                            modifier = Modifier.scale(0.5f)
                        )
                        VoiceCommandTrigger(
                            isListening = state.isListening,
                            onClick = { timeModel.onAction(TimeAction.StartVoiceCommand) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatchName(
    modifier: Modifier = Modifier,
    text: String
) {
    AppText(
        text = text.replace(Regex("(CASIO)"), "$1\n"),
        // text = "Watch Connected",
        fontSize = 48.sp,
        textAlign = TextAlign.Center, // Center-aligns each line
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
    )
}

@Composable
fun VoiceCommandTrigger(
    modifier: Modifier = Modifier,
    isListening: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.voice_assist),
            contentDescription = "Voice Command",
            modifier = Modifier.size(24.dp),
            tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewWatchName() {
    WatchNameView(Modifier)
}

