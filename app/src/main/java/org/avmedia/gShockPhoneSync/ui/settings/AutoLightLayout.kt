/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 11:56 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import org.avmedia.gShockPhoneSync.IHideableLayout
import org.avmedia.gshockapi.ble.Connection

class AutoLightLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), IHideableLayout {

    init {
        if (Connection.deviceName.contains("2100")) hide() else show()
    }

    override fun show() {
        visibility = View.VISIBLE
    }

    override fun hide() {
        visibility = View.GONE
    }
}
