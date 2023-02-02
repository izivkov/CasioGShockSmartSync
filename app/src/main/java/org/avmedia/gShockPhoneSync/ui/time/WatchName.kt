/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:12 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.content.Context
import android.util.AttributeSet
import org.avmedia.gShockPhoneSync.customComponents.CacheableSubscribableTextView
import org.avmedia.gshockapi.utils.Utils

class WatchName @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CacheableSubscribableTextView(context, attrs, defStyleAttr) {

    init {
        text = get(this.javaClass.simpleName)
        subscribe(this.javaClass.simpleName, "CASIO_WATCH_NAME")
    }

    override fun onDataReceived(value: String, name: String) {
        super.onDataReceived(Utils.toAsciiString(value, 1), name)
    }
}
