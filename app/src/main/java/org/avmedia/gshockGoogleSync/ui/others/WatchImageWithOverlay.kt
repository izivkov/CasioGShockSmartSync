package org.avmedia.gshockGoogleSync.ui.others

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.avmedia.gshockGoogleSync.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WatchImageWithOverlay(
    modifier: Modifier = Modifier,
    imageResId: Int = R.drawable.gw_b5600,
    arrowsVerticalPosition: Float = 0.55f
) {
    var isAnimating by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(5000)
        isAnimating = false
    }

    val infiniteTransition = rememberInfiniteTransition(label = "arrow fade")
    val arrowAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAnimating) 0.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow opacity"
    )

    BoxWithConstraints(modifier = modifier) {
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val arrowPositionPx = screenHeightPx * arrowsVerticalPosition
        val textOffsetPx = arrowPositionPx * 0.45f + with(LocalDensity.current) { 40.dp.toPx() }
        val textOffsetDp = with(LocalDensity.current) { textOffsetPx.toDp() }
        val horizontalPadding = 16.dp

        Image(
            painter = painterResource(id = imageResId),
            contentDescription = "Watch Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        OverlayText(
            text = stringResource(
                R.string.press_and_hold_for_3_seconds
            ),

            alignment = Alignment.BottomStart,
            verticalOffset = textOffsetDp,
            horizontalPadding = horizontalPadding
        )

        OverlayText(
            text = stringResource(
                R.string.short_press_to_run_actions
            ),
            alignment = Alignment.BottomEnd,
            verticalOffset = textOffsetDp,
            horizontalPadding = horizontalPadding
        )

        ArrowsOverlay(
            arrowAlpha = arrowAlpha,
            arrowsVerticalPosition = arrowsVerticalPosition
        )
    }
}

@Composable
fun WatchImageWithOverlayAlwaysConnected(
    modifier: Modifier = Modifier,
    imageResId: Int = R.drawable.gw_b5600,
) {
    BoxWithConstraints(modifier = modifier) {
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val textOffsetPx = screenHeightPx * 0.75f // Position text 3/4 down the screen

        // Background watch image
        Image(
            painter = painterResource(id = imageResId),
            contentDescription = "Watch Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Greeting where upstream showed "To run actions, start Find Phone on the watch".
        // Editable on the Settings tab: null = default text, "" = no greeting.
        val context = androidx.compose.ui.platform.LocalContext.current
        val greeting = remember { org.avmedia.gshockGoogleSync.ui.settings.StartGreeting.custom(context) }
            ?: stringResource(R.string.start_greeting)
        val scrolling = remember { org.avmedia.gshockGoogleSync.ui.settings.StartGreeting.scrolling(context) }
        if (greeting.isNotBlank() && scrolling) Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = with(LocalDensity.current) { textOffsetPx.toDp() }),
            contentAlignment = Alignment.TopStart
        ) {
            LedTicker(greeting)
        }
        else if (greeting.isNotBlank()) Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = with(LocalDensity.current) { textOffsetPx.toDp() }),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = greeting,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
fun OverlayText(
    text: String,
    alignment: Alignment,
    verticalOffset: Dp,
    horizontalPadding: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (alignment == Alignment.BottomStart) horizontalPadding else 0.dp,
                end = if (alignment == Alignment.BottomEnd) horizontalPadding else 0.dp,
                bottom = verticalOffset
            ),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun ArrowsOverlay(arrowAlpha: Float, arrowsVerticalPosition: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 20f

        // Left arrow
        drawArrow(
            startX = size.width * 0.03f,
            startY = size.height * arrowsVerticalPosition,
            endX = size.width * 0.10f,
            endY = size.height * arrowsVerticalPosition,
            arrowAlpha = arrowAlpha,
            strokeWidth = strokeWidth,
            isLeft = true
        )

        // Right arrow
        drawArrow(
            startX = size.width * 0.97f,
            startY = size.height * arrowsVerticalPosition,
            endX = size.width * 0.90f,
            endY = size.height * arrowsVerticalPosition,
            arrowAlpha = arrowAlpha,
            strokeWidth = strokeWidth,
            isLeft = false
        )
    }
}

fun DrawScope.drawArrow(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    arrowAlpha: Float,
    strokeWidth: Float,
    isLeft: Boolean
) {
    val arrowPath = Path().apply {
        moveTo(startX, startY)
        lineTo(endX, endY)

        val arrowLength = 20f
        val angle = 40f
        val angleRad = Math.toRadians(angle.toDouble())
        val perpOffset = strokeWidth / 2f

        val x1 =
            if (isLeft) endX - arrowLength * cos(angleRad).toFloat() else endX + arrowLength * cos(
                angleRad
            ).toFloat()
        val y1 = endY - arrowLength * sin(angleRad).toFloat() - perpOffset
        val x2 =
            if (isLeft) endX - arrowLength * cos(angleRad).toFloat() else endX + arrowLength * cos(
                angleRad
            ).toFloat()
        val y2 = endY + arrowLength * sin(angleRad).toFloat() + perpOffset

        moveTo(endX, endY)
        lineTo(x1, y1)
        moveTo(endX, endY)
        lineTo(x2, y2)
    }

    drawPath(
        path = arrowPath,
        color = Color.Red.copy(alpha = arrowAlpha),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round
        )
    )
}

/**
 * The greeting as an LED-style ticker: moves left to right in visible steps (a fixed jump every
 * [STEP_MS]), not smoothly; enters from the left edge again after leaving on the right.
 */
private const val STEP_MS = 120L
private val STEP = 6.dp

@Composable
private fun LedTicker(text: String) {
    val density = LocalDensity.current
    val stepPx = with(density) { STEP.toPx() }
    var textWidth by remember { mutableStateOf(0) }
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(STEP_MS)
            step++
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
        val widthPx = constraints.maxWidth
        val span = (widthPx + textWidth).coerceAtLeast(1)
        // Snap to whole steps: x jumps by stepPx each tick (no in-between frames).
        val x = ((step * stepPx).toInt() % span) - textWidth
        Text(
            text = text,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .onSizeChanged { textWidth = it.width }
                .offset { androidx.compose.ui.unit.IntOffset(x, 0) }
                .wrapContentWidth(Alignment.Start, unbounded = true),
        )
    }
}
