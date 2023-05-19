/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-23, 9:38 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity
import timber.log.Timber


/*
RESET:
1a 04 12 00 00 00                                             ..

COUNTERCLOCKWISE:
1a 04 18 1d 02 00

CLOCKWISE:
1a 04 19 1c 02 00


 */
open class HandReset @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageButton(context, attrs/*, defStyleAttr*/) {

    init {
        setOnTouchListener(OnTouchListener())
    }

    inner class OnTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    Timber.i("Reset to top")
                    runBlocking {
                        MainActivity.api().resetHand() }
                }
            }
            return false
        }
    }
}
