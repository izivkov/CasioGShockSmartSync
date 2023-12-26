package org.avmedia.gShockPhoneSync.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputFilter
import android.text.Spanned
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.ui.time.TimeFragment.Companion.getFragmentScope
import timber.log.Timber

class AdjustmentTimeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {
    init {
        setOnTouchListener(OnTouchListener())
    }

    // Wait for layout be be loaded, otherwise the layout will overwrite the values when loaded.
    override fun onFinishInflate() {
        super.onFinishInflate()
        if (api().isConnected() && api().isNormalButtonPressed()) {
            getFragmentScope().launch(Dispatchers.IO) {
                text = api().getAdjustmentTime().toString()
            }
        }
    }

    inner class OnTouchListener : View.OnTouchListener {
        @SuppressLint("SetTextI18n")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {

//                    val minutesInputView = dialogLayout.findViewById<EditText>(R.id.timer_minutes)
//                    minutesInputView.filters = arrayOf(
//                        InputFilter.LengthFilter(2), InputFilterMinMax(0, 59)
//                    )
                }
            }
            v?.performClick()
            return false
        }
    }

    class InputFilterMinMax(private var min: Int, private var max: Int) : InputFilter {

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            try {
                val input = Integer.parseInt(dest.toString() + source.toString())
                if (isInRange(min, max, input))
                    return null
            } catch (_: NumberFormatException) {
            }
            return ""
        }

        private fun isInRange(a: Int, b: Int, c: Int): Boolean {
            return if (b > a) c in a..b else c in b..a
        }
    }
}