package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import AppTextLarge
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import org.avmedia.gshockGoogleSync.h5600.H5600Prefs
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppSmallButton

/** The emoji you drag ("ride") on the cover screen to unlock. */
object RideEmoji {
    private const val KEY = "cover_ride_emoji"
    const val DEFAULT = "🚲"

    fun get(context: Context): String = H5600Prefs.get(context).getString(KEY, null) ?: DEFAULT

    fun set(context: Context, text: String) = H5600Prefs.get(context).edit().putString(KEY, text).apply()

    const val MAX = 14

    /** Characters as the user sees them (an emoji or accented letter counts as one). */
    fun graphemes(s: String): List<String> {
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(s)
        val out = mutableListOf<String>()
        var start = it.first()
        var end = it.next()
        while (end != java.text.BreakIterator.DONE) { out += s.substring(start, end); start = end; end = it.next() }
        return out
    }

    fun limit(s: String): String = graphemes(s).take(MAX).joinToString("")
}

/** Settings tab: "What are we riding?" — any text up to 14 characters (emoji welcome). */
@Composable
fun RideCard() {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(RideEmoji.get(context)) }
    var picking by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppTextLarge(text = "What are we riding?")
            androidx.compose.material3.OutlinedTextField(
                value = chosen,
                onValueChange = { v ->
                    chosen = RideEmoji.limit(v)
                    if (chosen.isNotBlank()) RideEmoji.set(context, chosen)
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp),
                supportingText = { Text("${RideEmoji.graphemes(chosen).size}/${RideEmoji.MAX}") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(text = "Drag it right on the cover screen to unlock.", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppSmallButton(text = "Emoji…", onClick = { picking = true })
                    AppSmallButton(text = "Default", onClick = { chosen = RideEmoji.DEFAULT; RideEmoji.set(context, RideEmoji.DEFAULT) })
                }
            }
        }
    }
    if (picking) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("What are we riding?") },
            text = {
                // Every emoji, by category, with search and recently used (AndroidX emoji picker).
                AndroidView(
                    factory = { ctx ->
                        EmojiPickerView(ctx).apply {
                            setOnEmojiPickedListener { item ->
                                // Adds the emoji to the text (still at most 14 characters).
                                chosen = RideEmoji.limit(chosen + item.emoji)
                                RideEmoji.set(context, chosen)
                                picking = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                )
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}
