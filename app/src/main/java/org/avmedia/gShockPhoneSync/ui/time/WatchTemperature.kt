/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:12 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.Build
import android.telephony.TelephonyManager
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.ui.time.TimeFragment.Companion.getFragmentScope
import org.avmedia.gshockapi.WatchInfo
import java.util.*

class WatchTemperature @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("SetTextI18n")
    override fun onFinishInflate() {
        super.onFinishInflate()

        visibility = if (WatchInfo.worldCities)
            View.VISIBLE
        else {
            View.GONE
            return
        }


        if (api().isConnected() && api().isNormalButtonPressed()) {
            getFragmentScope().launch(Dispatchers.IO) {
                val temperature = api().getWatchTemperature()

                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val countryCodeValue = tm.networkCountryIso
                val isUS = (countryCodeValue.isNotEmpty() && countryCodeValue.uppercase() == "US")
                val fmt =
                    MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
                val measure = if (isUS) Measure(
                    ((temperature * 9 / 5) + 32),
                    MeasureUnit.FAHRENHEIT
                ) else Measure(temperature, MeasureUnit.CELSIUS)

                getFragmentScope().launch(Dispatchers.Main) {
                    text = fmt.format(measure)
                }
            }
        }
    }
}

