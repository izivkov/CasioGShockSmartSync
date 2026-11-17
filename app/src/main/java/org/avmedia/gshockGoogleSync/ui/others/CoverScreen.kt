package org.avmedia.gshockGoogleSync.ui.others

import AppText
import AppTextVeryLarge
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard

/**
 * "Ride to unlock" with the bicycle on its own line below, so nothing is in its way. Only the
 * bicycle reacts, like a slider: drag it to the right past
 * [RIDE_DISTANCE] to unlock (short buzz); let go earlier and it rolls back.
 */
private val RIDE_DISTANCE = 96.dp

@Composable
private fun RideToUnlock(onUnlock: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rideDistancePx = with(density) { RIDE_DISTANCE.toPx() }
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var unlocked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppTextVeryLarge(text = stringResource(R.string.cover_ride_to_unlock))
        androidx.compose.material3.Text(
            text = "🚲",
            fontSize = 48.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .offset { androidx.compose.ui.unit.IntOffset(offset.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value + dx).coerceIn(0f, rideDistancePx * 1.5f)) }
                        },
                        onDragEnd = {
                            if (offset.value >= rideDistancePx && !unlocked) {
                                unlocked = true
                                unlockBuzz(context) // no beep: a short vibration confirms the unlock
                                onUnlock()
                            }
                            scope.launch { offset.animateTo(0f) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    )
                },
        )
        // Same look as the start-screen greeting (bold red).
        androidx.compose.material3.Text(
            text = stringResource(R.string.cover_signature),
            color = androidx.compose.ui.graphics.Color.Red,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
        )
    }
}

@Composable
fun CoverScreen(
    onUnlock: () -> Unit,
    isConnected: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
            RideToUnlock(onUnlock)

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

/** A short, firm buzz (independent of the system "touch feedback" setting). */
private fun unlockBuzz(context: android.content.Context) {
    runCatching {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
