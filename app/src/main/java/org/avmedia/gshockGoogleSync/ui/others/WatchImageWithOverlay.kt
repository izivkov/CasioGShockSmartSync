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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    // Stop animation after 3 seconds
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

    BoxWithConstraints(
        modifier = modifier
    ) {
        // Base image
        Image(
            painter = painterResource(id = imageResId),
            contentDescription = "Watch Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Left text overlay - moved below arrow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = stringResource(R.string.press_and_hold_for_3_seconds),
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = this@BoxWithConstraints.maxHeight * 0.30f)
            )
        }

        // Right text overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = stringResource(R.string.short_press_to_run_actions),
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = this@BoxWithConstraints.maxHeight * 0.30f)
            )
        }

        // Arrows overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 20f // Define stroke width here for both arrows

            // Left arrow
            val leftArrowPath = Path().apply {
                val startX = size.width * 0.03f
                val startY = size.height * arrowsVerticalPosition
                val endX = size.width * 0.10f
                val endY = startY

                // Main line
                moveTo(startX, startY)
                lineTo(endX, endY)

                // Arrow head calculations
                val arrowLength = 20f
                val angle = 40f
                val angleRad = Math.toRadians(angle.toDouble())

                // Adjusted calculations for inward-pointing arrow head
                val perpOffset = strokeWidth / 2f

                val x1 = endX - arrowLength * cos(angleRad).toFloat()
                val y1 = endY - arrowLength * sin(angleRad).toFloat() - perpOffset
                val x2 = endX - arrowLength * cos(angleRad).toFloat()
                val y2 = endY + arrowLength * sin(angleRad).toFloat() + perpOffset

                // Arrow head lines
                moveTo(endX, endY)
                lineTo(x1, y1)
                moveTo(endX, endY)
                lineTo(x2, y2)
            }

            // Right arrow
            val rightArrowPath = Path().apply {
                val startX = size.width * 0.97f
                val startY = size.height * arrowsVerticalPosition
                val endX = size.width * 0.90f
                val endY = startY

                // Main line
                moveTo(startX, startY)
                lineTo(endX, endY)

                // Arrow head calculations
                val arrowLength = 20f
                val angle = 40f
                val angleRad = Math.toRadians(angle.toDouble())

                // Adjusted calculations for inward-pointing arrow head
                val perpOffset = strokeWidth / 2f

                val x1 = endX + arrowLength * cos(angleRad).toFloat()
                val y1 = endY - arrowLength * sin(angleRad).toFloat() - perpOffset
                val x2 = endX + arrowLength * cos(angleRad).toFloat()
                val y2 = endY + arrowLength * sin(angleRad).toFloat() + perpOffset

                // Arrow head lines
                moveTo(endX, endY)
                lineTo(x1, y1)
                moveTo(endX, endY)
                lineTo(x2, y2)
            }

            // Draw both arrows (unchanged)
            drawPath(
                path = leftArrowPath,
                color = Color.Red.copy(alpha = arrowAlpha),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )

            drawPath(
                path = rightArrowPath,
                color = Color.Red.copy(alpha = arrowAlpha),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}