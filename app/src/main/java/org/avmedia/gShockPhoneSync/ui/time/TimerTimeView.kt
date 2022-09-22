package org.avmedia.gShockPhoneSync.ui.time

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputFilter
import android.text.Spanned
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.casio.WatchDataCollector
import org.avmedia.gShockPhoneSync.customComponents.CacheableSubscribableTextView
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import org.jetbrains.anko.runOnUiThread
import org.json.JSONObject
import timber.log.Timber


class TimerTimeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    init {
        setOnTouchListener(OnTouchListener())
        val timer = WatchDataCollector.timerValue
        text = makeLongString(timer.toInt())
    }

    private fun makeLongString(inSeconds: Int): String {
        val hours = inSeconds / 3600
        val minutesAndSeconds = inSeconds % 3600
        val minutes = minutesAndSeconds / 60
        val seconds = minutesAndSeconds % 60

        return "${"%02d".format(hours)}:${"%02d".format(minutes)}:${"%02d".format(seconds)}"
    }

    inner class OnTouchListener : View.OnTouchListener {
        @SuppressLint("SetTextI18n")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {

                    val dialogLayout =
                        LayoutInflater.from(context).inflate(R.layout.timer_input, null)

                    val hoursInputView = dialogLayout.findViewById<EditText>(R.id.timer_hours)
                    hoursInputView.filters = arrayOf<InputFilter>(
                        InputFilter.LengthFilter(2), InputFilterMinMax(0, 23)
                    )

                    val minutesInputView = dialogLayout.findViewById<EditText>(R.id.timer_minutes)
                    minutesInputView.filters = arrayOf<InputFilter>(
                        InputFilter.LengthFilter(2), InputFilterMinMax(0, 59)
                    )

                    val secondsInputView = dialogLayout.findViewById<EditText>(R.id.timer_seconds)
                    secondsInputView.filters = arrayOf<InputFilter>(
                        InputFilter.LengthFilter(2), InputFilterMinMax(0, 59)
                    )

                    val dialog = MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.timerDialogTitle)
                        .setView(dialogLayout)
                        .setNeutralButton(resources.getString(R.string.cancel)) { _, _ ->
                            Timber.i("Cancel pressed...")
                        }
                        .setPositiveButton(resources.getString(R.string.ok)) { _, _ ->
                            var hours = 0
                            if (hoursInputView.text.isNotEmpty()) {
                                hours = (hoursInputView.text.toString()).toInt()
                            }

                            var minutes = 0
                            if (minutesInputView.text.isNotEmpty()) {
                                minutes = (minutesInputView.text.toString()).toInt()
                            }

                            var seconds = 0
                            if (secondsInputView.text.isNotEmpty()) {
                                seconds = (secondsInputView.text.toString()).toInt()
                            }

                            text = "${"%02d".format(hours)}:${"%02d".format(minutes)}:${
                                "%02d".format(seconds)
                            }"

                            TimerModel.set(text.toString())
                        }

                    dialog.show()
                }
            }
            v?.performClick()
            return false
        }
    }

    class InputFilterMinMax : InputFilter {
        private var min: Int = 0
        private var max: Int = 0

        constructor(min: Int, max: Int) {
            this.min = min
            this.max = max
        }

        constructor(min: String, max: String) {
            this.min = Integer.parseInt(min)
            this.max = Integer.parseInt(max)
        }

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
            } catch (nfe: NumberFormatException) {
            }
            return ""
        }

        private fun isInRange(a: Int, b: Int, c: Int): Boolean {
            return if (b > a) c in a..b else c in b..a
        }
    }
}