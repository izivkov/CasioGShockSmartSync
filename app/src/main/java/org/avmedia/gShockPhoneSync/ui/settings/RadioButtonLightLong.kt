package org.avmedia.gShockPhoneSync.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import org.avmedia.gshockapi.WatchInfo

@SuppressLint("AppCompatCustomView")
class RadioButtonLightLong @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : android.widget.RadioButton(context, attrs) {

    init {
        text = WatchInfo.longLightDuration
    }
}
