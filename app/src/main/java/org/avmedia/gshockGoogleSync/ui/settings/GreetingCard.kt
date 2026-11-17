package org.avmedia.gshockGoogleSync.ui.settings

import AppSwitch
import AppText
import AppTextLarge
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.h5600.H5600Prefs
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppSmallButton

/** The start-screen greeting: null = the default ("Hello, smart ass"), "" = no greeting. */
object StartGreeting {
    private const val KEY = "start_greeting"
    private const val KEY_SCROLL = "start_greeting_scroll"

    /** Ticker: the text steps from left to right instead of standing still. */
    fun scrolling(context: Context): Boolean = H5600Prefs.get(context).getBoolean(KEY_SCROLL, false)

    fun setScrolling(context: Context, on: Boolean) = H5600Prefs.get(context).edit().putBoolean(KEY_SCROLL, on).apply()

    fun custom(context: Context): String? = H5600Prefs.get(context).getString(KEY, null)

    fun save(context: Context, text: String?) =
        H5600Prefs.get(context).edit().apply { if (text == null) remove(KEY) else putString(KEY, text) }.apply()
}

/** Settings tab: edit the greeting on the start screen; saved as you type. */
@Composable
fun GreetingCard() {
    val context = LocalContext.current
    val default = stringResource(R.string.start_greeting)
    var text by remember { mutableStateOf(StartGreeting.custom(context) ?: default) }
    var scrolling by remember { mutableStateOf(StartGreeting.scrolling(context)) }
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppTextLarge(text = "Start screen greeting")
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.take(60); StartGreeting.save(context, text) },
                singleLine = true,
                placeholder = { Text("(no greeting)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "Shown while the app waits for the watch. Empty = none.", fontSize = 13.sp, modifier = Modifier.weight(1f))
                AppSmallButton(text = "Default", onClick = { StartGreeting.save(context, null); text = default })
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "Scrolling (LED ticker, left to right)")
                AppSwitch(checked = scrolling, onCheckedChange = { scrolling = it; StartGreeting.setScrolling(context, it) })
            }
        }
    }
}
