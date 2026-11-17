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
import androidx.compose.ui.graphics.graphicsLayer
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
    // Dragging the wrong way (left): the bicycle spins, faster the further it is pulled, with a
    // short buzz per full turn; on release it finishes the turn and stops upright.
    val spin = remember { androidx.compose.animation.core.Animatable(0f) }
    var leftPull by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var held by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var last = 0L
        var turns = 0
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (last == 0L) 0f else (now - last) / 1e9f
            last = now
            val pullDp = leftPull / density.density
            if (held && pullDp > SPIN_START_DP) {
                // 360°/s at the start, +40°/s per dp pulled, up to 15 turns a second.
                val speed = (360f + 40f * (pullDp - SPIN_START_DP)).coerceAtMost(5400f)
                spin.snapTo(spin.value - speed * dt) // backwards: the wrong way
                val t = (-spin.value / 360f).toInt()
                if (t != turns) { turns = t; turnTick(context) }
            } else if (!held && spin.value != 0f && !spin.isRunning) {
                // Finish the current turn, then reset.
                val end = kotlin.math.floor(spin.value / 360f) * 360f
                spin.animateTo(end, androidx.compose.animation.core.tween(250))
                spin.snapTo(0f)
                turns = 0
            }
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var unlocked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Both texts move left exactly as far as the bicycle moves right (and spring back with it).
        val counter = Modifier.offset { androidx.compose.ui.unit.IntOffset(-offset.value.toInt(), 0) }
        AppTextVeryLarge(text = stringResource(R.string.cover_ride_to_unlock), modifier = counter)
        androidx.compose.material3.Text(
            text = "🚲",
            fontSize = 48.sp,
            modifier = Modifier
                // Visually equal gaps above and below the bicycle (measured on screen: the fonts' own
                // line spacing differs, so the bottom needs more than the top).
                .padding(top = 8.dp, bottom = 22.5.dp)
                .offset { androidx.compose.ui.unit.IntOffset(offset.value.toInt(), 0) }
                .graphicsLayer {
                    rotationZ = spin.value
                    // Pivot a bit above the text box centre. The bicycle still swings around a little
                    // while spinning (the emoji is not centred in its box); the user liked this look.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.34f)
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            if (offset.value <= 0f && (dx < 0f || leftPull > 0f)) {
                                // Pulling left from the start position: the pull distance sets the spin speed.
                                held = true
                                leftPull = (leftPull - dx).coerceAtLeast(0f)
                            } else {
                                leftPull = 0f
                                scope.launch { offset.snapTo((offset.value + dx).coerceAtLeast(0f)) } // no upper limit: rides off the screen edge
                            }
                        },
                        onDragEnd = {
                            held = false; leftPull = 0f // let the spin finish its turn
                            if (offset.value >= rideDistancePx && !unlocked) {
                                unlocked = true
                                unlockBuzz(context) // no beep: a short vibration confirms the unlock
                                onUnlock()
                            }
                            scope.launch { offset.animateTo(0f) }
                        },
                        onDragCancel = { held = false; leftPull = 0f; scope.launch { offset.animateTo(0f) } },
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
            modifier = counter,
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

/** The left pull (dp) after which the bicycle starts spinning. */
private const val SPIN_START_DP = 12f

/** One short tick per full turn: faster spinning, faster ticking. */
private fun turnTick(context: android.content.Context) {
    runCatching {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(25, 255))
    }
}
