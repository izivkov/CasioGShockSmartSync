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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WatchImageWithOverlay(
    modifier: Modifier = Modifier,
    imageResId: Int = R.drawable.gw_b5600,
    arrowsVerticalPosition: Float = 0.55f,
    ptrConnectionViewModel: PreConnectionViewModel = viewModel()
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
            text = ptrConnectionViewModel.translateApi.stringResource(
                LocalContext.current,
                R.string.press_and_hold_for_3_seconds
            ),

            alignment = Alignment.BottomStart,
            verticalOffset = textOffsetDp,
            horizontalPadding = horizontalPadding
        )

        OverlayText(
            text = ptrConnectionViewModel.translateApi.stringResource(
                LocalContext.current,
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
    translateApi: TranslateRepository,
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

        // Centered text overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = with(LocalDensity.current) { textOffsetPx.toDp() }),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = translateApi.stringResource(
                    LocalContext.current,
                    R.string.find_phone_instruction
                ),
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
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