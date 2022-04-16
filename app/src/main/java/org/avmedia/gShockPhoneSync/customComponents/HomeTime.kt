/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:24 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport

open class HomeTime @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CacheableSubscribableTextView(context, attrs, defStyleAttr) {

    init {
        text = get(this.javaClass.simpleName)
    }

    override fun init() {
        CasioSupport.requestHomeTime()
    }

    init {
        subscribe(this.javaClass.simpleName, "CASIO_WORLD_CITIES")
    }

    override fun onDataReceived(data: String, name: String) {
        // only the first city is the home time location, handle 0x1f, 0x0 only.
        if (data.split(" ")[1].toInt() != 0) {
            return
        }
        super.onDataReceived(data, name)
    }
}
