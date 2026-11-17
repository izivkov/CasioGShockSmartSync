package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.avmedia.gshockGoogleSync.h5600.AlarmPool.PhoneAlarm
import org.avmedia.gshockGoogleSync.utils.AppTimeFormat
import timber.log.Timber

/**
 * A small card floating at the top of the screen, over any app, while a watch alarm may be
 * snoozing: "Stop snooze" or hide. Unlike a heads-up notification it stays until acted on or the
 * snooze window ends. Needs "Display over other apps"; Android never draws it over the lock screen
 * (the notification covers that). Plain Views: the window has no activity / lifecycle for Compose.
 */
object SnoozeOverlay {
    private val main = Handler(Looper.getMainLooper())
    private val views = mutableMapOf<String, View>() // alarm id -> card
    private val hiddenByUser = mutableSetOf<String>() // "×" for this snooze: do not show it again

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Opens the system screen where the user allows "Display over other apps" for this app. */
    fun requestPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Shows a card for each alarm in [snoozing] and removes the others. Any thread. */
    fun update(context: Context, snoozing: List<PhoneAlarm>, onStop: (String) -> Unit) = main.post {
        val app = context.applicationContext
        val ids = snoozing.map { it.id }.toSet()
        (views.keys - ids).forEach { remove(app, it) }
        hiddenByUser.retainAll(ids) // a later snooze of the same alarm shows again
        if (!canShow(app)) return@post
        snoozing.filter { it.id !in views && it.id !in hiddenByUser }.forEach { a -> show(app, a, onStop) }
    }

    fun dismiss(context: Context, alarmId: String) = main.post { remove(context.applicationContext, alarmId) }

    private fun remove(context: Context, id: String) {
        views.remove(id)?.let { v ->
            runCatching { context.getSystemService(WindowManager::class.java).removeView(v) }
                .onFailure { Timber.w(it, "snooze overlay remove") }
        }
    }

    private fun show(context: Context, a: PhoneAlarm, onStop: (String) -> Unit) {
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

        val title = TextView(context).apply {
            text = "⏰ " + AppTimeFormat.hm(a.hour, a.minute) + (if (a.label.isEmpty()) "" else " · ${a.label}") + " may be snoozing"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val stop = Button(context).apply {
            text = "Stop snooze"
            setOnClickListener { remove(context, a.id); onStop(a.id) }
        }
        val hide = Button(context).apply {
            text = "×"
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { hiddenByUser += a.id; remove(context, a.id) }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#E6202124")) }
            elevation = dp(6).toFloat()
            addView(title)
            addView(stop)
            addView(hide)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: the app underneath keeps the keyboard and touches outside the card.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = dp(48) // below the status bar
            horizontalMargin = 0.03f
        }
        runCatching { wm.addView(card, params); views[a.id] = card }
            .onFailure { Timber.w(it, "snooze overlay show") }
    }
}

/**
 * Asks for "Display over other apps" when the app starts, until it is granted or the user picks
 * "Don't ask again". Android only grants it on its own settings screen, so "Allow" opens that.
 */
@androidx.compose.runtime.Composable
fun OverlayPermissionPrompt() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { H5600Prefs.get(context) }
    var show by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            !SnoozeOverlay.canShow(context) && !prefs.getBoolean("overlay_prompt_never", false),
        )
    }
    if (!show) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { show = false }, // asked again on the next launch
        title = { androidx.compose.material3.Text("Show the snooze card over other apps?") },
        text = {
            androidx.compose.material3.Text(
                "While a watch alarm may be snoozing, a small card with \"Stop snooze\" can float on top of " +
                    "whatever app is open. Android needs \"Display over other apps\" for that — tap Allow, " +
                    "then switch it on for this app.",
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { show = false; SnoozeOverlay.requestPermission(context) }) {
                androidx.compose.material3.Text("Allow")
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(onClick = {
                    prefs.edit().putBoolean("overlay_prompt_never", true).apply(); show = false
                }) { androidx.compose.material3.Text("Don't ask again") }
                androidx.compose.material3.TextButton(onClick = { show = false }) { androidx.compose.material3.Text("Not now") }
            }
        },
    )
}
