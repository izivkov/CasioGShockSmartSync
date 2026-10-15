package org.avmedia.gshockGoogleSync.ui.time

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager

@Composable
fun Battery(timeModel: TimeViewModel = hiltViewModel()) {
    val state by timeModel.state.collectAsState()
    val batteryLevel = state.batteryLevel.coerceIn(0, 100)
    val watchFeatureManager = LocalWatchFeatureManager.current
    if (!watchFeatureManager.isFeatureSupported("time.battery")) return
    val description = stringResource(R.string.battery_level)
    val stripes = ImageBitmap.imageResource(R.drawable.stripes)

    Canvas(
        modifier = Modifier
            .width(60.dp)
            .height(20.dp)
            .semantics {
                contentDescription = description
                stateDescription = "$batteryLevel%"
                role = Role.Image
            }
    ) {
        val strokeWidth = size.height * 0.08f
        val capWidth = size.width * 0.08f
        val capHeight = size.height * 0.5f
        val cornerRadius = size.height * 0.12f
        val bodyLeft = 0f
        val bodyRight = size.width - capWidth
        val bodyTop = strokeWidth / 2
        val bodyBottom = size.height - strokeWidth / 2
        val bodyLeftWithStroke = bodyLeft + strokeWidth / 2
        val bodyWidth = bodyRight - bodyLeftWithStroke - strokeWidth / 2
        val bodyHeight = bodyBottom - bodyTop
        val capTop = (size.height - capHeight) / 2

        drawRoundRect(
            color = Color(0xFF9E9E9E),
            topLeft = Offset(bodyRight, capTop),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )
        drawRoundRect(
            color = Color(0xFF9E9E9E),
            topLeft = Offset(bodyLeftWithStroke, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = strokeWidth)
        )

        val fillLeft = bodyLeft + strokeWidth
        val fillRight = fillLeft + (bodyRight - strokeWidth - fillLeft) * batteryLevel / 100f
        val fillTop = bodyTop + strokeWidth / 2
        val fillBottom = bodyBottom - strokeWidth / 2

        clipRect(fillLeft, fillTop, fillRight, fillBottom) {
            drawImage(
                image = stripes,
                dstOffset = IntOffset(fillLeft.toInt(), fillTop.toInt()),
                dstSize = IntSize(
                    (fillRight - fillLeft).toInt(),
                    (fillBottom - fillTop).toInt()
                )
            )
        }
    }
}
