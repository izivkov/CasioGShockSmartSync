package org.avmedia.gshockGoogleSync.ui.others

import AppText
import AppTextVeryLarge
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard

@Composable
private fun LongPressHandler(
    onPressed: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown()
                    onPressed(true)
                    val longPress = awaitLongPressOrCancellation(down.id)
                    onPressed(false)
                    if (longPress != null) {
                        onLongPress()
                    }
                }
            }
        }
    ) {
        content()
    }
}

@Composable
private fun UnlockText(
    isPressed: Boolean
) {
    AppTextVeryLarge(
        text = stringResource(
            R.string.cover_hold_to_unlock
        ),
        color = if (isPressed)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else
            MaterialTheme.colorScheme.primary
    )
}

@Composable
fun CoverScreen(
    onUnlock: () -> Unit,
    isConnected: Boolean = true
) {
    val toneGen = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }
    var isPressed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            toneGen.release()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            LongPressHandler(
                onPressed = { isPressed = it },
                onLongPress = {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                    onUnlock()
                }
            ) {
                UnlockText(isPressed)
            }

            if (isConnected) {
                AppCard(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                ) {
                    AppText(
                        text = stringResource(
                            R.string.cover_connected
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
