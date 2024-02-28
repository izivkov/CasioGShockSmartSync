/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:12 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.content.Context
import android.util.AttributeSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gshockapi.WatchInfo

class WatchName @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (api().isConnected() && api().isNormalButtonPressed()) {
            TimeFragment.getFragmentScope().launch(Dispatchers.IO) {
                text = WatchInfo.name
            }
        }
    }
}
