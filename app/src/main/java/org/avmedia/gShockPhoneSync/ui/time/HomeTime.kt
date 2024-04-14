/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:24 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.ui.time.TimeFragment.Companion.getFragmentScope
import org.avmedia.gshockapi.WatchInfo

open class HomeTime @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    // Wait for layout be be loaded, otherwise the layout will overwrite the values when loaded.
    override fun onFinishInflate() {
        super.onFinishInflate()
        if (api().isConnected() && api().isNormalButtonPressed()) {
            getFragmentScope().launch(Dispatchers.IO) {
                text = if (WatchInfo.worldCities) api().getHomeTime() else "N/A"
            }
        }
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        (context as Activity).runOnUiThread {
            super.setText(text, type)
        }
    }

    fun update() {
        getFragmentScope().launch(Dispatchers.IO) {
            text = api().getHomeTime()
        }
    }
}
