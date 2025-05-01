package org.avmedia.gshockGoogleSync.ui.others

import AppText
import AppTextVeryLarge
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.ui.common.AppCard

@Composable
fun CoverScreen(
    translateApi: TranslateRepository,
    onUnlock: () -> Unit,
    isConnected: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var isComplete by remember { mutableStateOf(false) }

                AppCard {
                    Box(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown()
                                        val longPress = awaitLongPressOrCancellation(down.id)
                                        if (longPress != null) {
                                            onUnlock()
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 32.dp, vertical = 16.dp)
                    ) {
                        AppTextVeryLarge(
                            text = "Hold to unlock"
                        )
                    }
                }
            }

            if (isConnected) {
                AppCard(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 48.dp)
                ) {
                    AppText(
                        text = "Connected",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
