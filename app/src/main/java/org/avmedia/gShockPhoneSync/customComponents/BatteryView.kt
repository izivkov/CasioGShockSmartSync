/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:14 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.PaintDrawable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport
import org.avmedia.gShockPhoneSync.casioB5600.WatchDataCollector
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import org.jetbrains.anko.runOnUiThread
import timber.log.Timber

class BatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    View(context, attrs, defStyleAttr) {
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

    // Percent
    private var percentPaint = Paint()
    private var percentRect = RectF()
    private var percentRectTopMin = 0f
    private var percent: Int = 0
    private var percentageBitmap: Bitmap? = null

    object CashedPercent {
        var percent = 0
        var isSet = false
    }

    init {
        percentageBitmap = getBitmap(R.drawable.stripes)
        if (!CashedPercent.isSet) {
            subscribe("CASIO_WATCH_CONDITION", ::onDataReceived)
        }
        setPercent(CashedPercent.percent)
        createAppEventsSubscription()
    }

    private fun createAppEventsSubscription(): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                when (it) {
                    ProgressEvents.Events.WatchInitializationCompleted -> {
                        CasioSupport.requestBatteryLevel()
                    }
                }
            }
            .subscribe(
                { },
                { throwable -> Timber.i("Got error on subscribe: $throwable") })


    @SuppressLint("CheckResult")
    private fun subscribe(subject: String, onDataReceived: (String) -> Unit) {
        WatchDataEvents.addSubject(subject)
        WatchDataEvents.subscribe(this.javaClass.simpleName, subject, onNext = {
            onDataReceived(it as String)
        })
    }

    private fun onDataReceived(data: String) {
        percent = 0
        var cmdInts = Utils.toIntArray(data)
        // command looks like 0x28 13 1E 00.
        // 50% level is obtain from the second Int 13:
        // 0x13 = 0b00010011
        // take MSB 0b0001. If it is not 0, we have 50% charge
        val MASK_50_PERCENT = 0b00010000
        percent += if (cmdInts[1] or MASK_50_PERCENT != 0) 50 else 0

        // Fine value is obtained from the 3rd integer, 0x1E. The LSB (0xE) represents
        // the fine value between 0 and 0xf, which is the other 50%. So, to
        // get this value, we have 50% * 0xe / 0xf. We add this to the previous battery level.
        // So, for command 0x28 13 1E 00, our battery level would be:
        // 50% (from 0x13) + 47 = 97%
        // The 47 number was obtained from 50 * 0xe / 0xf or 50 * 14/15 = 46.66

        val MASK_FINE_VALUE = 0xf
        val fineValue = cmdInts[2] and MASK_FINE_VALUE
        percent += 50 * fineValue / 15

        CashedPercent.percent = percent
        CashedPercent.isSet = true
        setPercent(percent)
    }

    @SuppressLint("DrawAllocation")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measureWidth = View.getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
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

        val pctRect = RectF(percentRect.left, percentRect.top, percentRect.right, percentRect.bottom)
        percentageBitmap?.let {
            canvas.drawBitmap(it, null, pctRect, null)
        }
    }

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

    private fun setPercent(percent: Int) {
        this.percent = percent
        invalidate()
    }
}