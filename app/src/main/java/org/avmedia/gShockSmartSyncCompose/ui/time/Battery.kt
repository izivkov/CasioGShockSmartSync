/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:14 p.m.
 */

@file:Suppress("SameParameterValue", "SameParameterValue", "SameParameterValue")

package org.avmedia.gShockSmartSyncCompose.ui.time

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.PaintDrawable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gshockapi.WatchInfo

@Composable
fun Battery(timeModel: TimeViewModel = viewModel()) {

    val batteryLevel by timeModel.batteryLevel.collectAsState()
    var result by remember { mutableIntStateOf(0) }

    LaunchedEffect(batteryLevel) {
        val percent = batteryLevel
        result = percent
    }

    AndroidView(
        modifier = Modifier
            .width(20.dp)
            .rotate(90f)
            .wrapContentHeight(),
        factory = { context ->
            BatteryView(context)
        },
        update = { batteryView ->
            // Update the percent value when result changes
            batteryView.setPercent(result)
        }
    )
}

@Suppress("SameParameterValue")
class BatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private var radius: Float = 0f

    // Top
    private var topPaint =
        PaintDrawable(ContextCompat.getColor(context, R.color.grey_500))

    private var topRect = Rect()
    private var topPaintWidthPercent = 50
    private var topPaintHeightPercent = 8

    // Border
    private var borderPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.grey_500)
        style = Paint.Style.STROKE
    }
    private var borderRect = RectF()
    private var borderStrokeWidthPercent = 4
    private var borderStroke: Float = 0f

    private var percentRect = RectF()
    private var percentRectTopMin = 0f
    private var percent: Int = 0
    private var percentageBitmap: Bitmap? = null

    init {
        percentageBitmap = getBitmap(R.drawable.stripes)
    }

    @SuppressLint("DrawAllocation")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measureWidth = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val measureHeight = (measureWidth * 3f).toInt()
        setMeasuredDimension(measureWidth, measureHeight)

        radius = borderStroke / 2
        borderStroke = (borderStrokeWidthPercent * measureWidth).toFloat() / 50

        // Top
        val topLeft = measureWidth * ((100 - topPaintWidthPercent) / 2) / 100
        val topRight = measureWidth - topLeft
        val topBottom = topPaintHeightPercent * measureHeight / 100
        topRect = Rect(topLeft, 0, topRight, topBottom)

        // Border
        val borderLeft = borderStroke / 2
        val borderTop = topBottom.toFloat() + borderStroke / 2
        val borderRight = measureWidth - borderStroke / 2
        val borderBottom = measureHeight - borderStroke / 2
        borderRect = RectF(borderLeft, borderTop, borderRight, borderBottom)

        // Progress
        val progressLeft = borderStroke
        percentRectTopMin = topBottom + borderStroke
        val progressRight = measureWidth - borderStroke
        val progressBottom = measureHeight - borderStroke
        percentRect = RectF(progressLeft, percentRectTopMin, progressRight, progressBottom)
    }

    override fun onDraw(canvas: Canvas) {
        if (!WatchInfo.hasBatteryLevel) {
            return
        }

        drawTop(canvas)
        drawBody(canvas)
        drawProgress(canvas, percent)
    }

    private fun drawTop(canvas: Canvas) {
        topPaint.bounds = topRect
        topPaint.setCornerRadii(floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f))
        topPaint.draw(canvas)
    }

    private fun drawBody(canvas: Canvas) {
        borderPaint.strokeWidth = borderStroke
        canvas.drawRoundRect(borderRect, radius, radius, borderPaint)
    }

    private fun drawProgress(canvas: Canvas, percent: Int) {
        percentRect.top =
            percentRectTopMin + (percentRect.bottom - percentRectTopMin) * (100 - percent) / 100

        val pctRect =
            RectF(percentRect.left, percentRect.top, percentRect.right, percentRect.bottom)
        percentageBitmap?.let {
            canvas.drawBitmap(it, null, pctRect, null)
        }
    }

    @Suppress("SameParameterValue")
    private fun getBitmap(
        drawableId: Int,
        desireWidth: Int? = null,
        desireHeight: Int? = null
    ): Bitmap? {
        val drawable = AppCompatResources.getDrawable(context, drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(
            desireWidth ?: drawable.intrinsicWidth,
            desireHeight ?: drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun setPercent(percent: Int) {
        this.percent = percent
        invalidate()
    }
}