package org.avmedia.gShockPhoneSync.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import org.avmedia.gShockPhoneSync.IHideableLayout
import org.avmedia.gshockapi.WatchInfo

@SuppressLint("AppCompatCustomView")
class RadioButtonLightShort @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : android.widget.RadioButton(context, attrs) {

    init {
        text = WatchInfo.shortLightDuration
    }
}
