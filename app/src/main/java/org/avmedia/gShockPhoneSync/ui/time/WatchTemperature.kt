/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:12 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.LocaleData
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.customComponents.CacheableSubscribableTextView
import java.text.DecimalFormat
import java.util.*


class WatchTemperature @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CacheableSubscribableTextView(context, attrs, defStyleAttr) {

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("SetTextI18n")
    override fun onFinishInflate() {
        super.onFinishInflate()

        if (api().isConnected() && api().isNormalButtonPressed()) {
            runBlocking {
                val temperature = api().getWatchTemperature()
                val fmt = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
                val ms = LocaleData.getMeasurementSystem(ULocale.forLocale(Locale.getDefault()))
                val measureF = if (ms == LocaleData.MeasurementSystem.US) Measure(((temperature * 9 / 5) + 32), MeasureUnit.FAHRENHEIT) else Measure(temperature, MeasureUnit.CELSIUS)
                text = fmt.format(measureF)
            }
        }
    }
}
