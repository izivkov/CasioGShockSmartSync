package org.avmedia.smartsync.homeassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One card of the Home Assistant screen: a title (with an optional icon) and its content. */
@Composable
internal fun HaCard(title: String, iconRes: Int? = null, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                iconRes?.let { AppIconFromResource(resourceId = it) }
                AppTextLarge(text = title)
            }
            content()
        }
    }
}

/** The screen title: the Home Assistant icon and the name. */
@Composable
internal fun HaTitle(text: String, iconRes: Int = R.drawable.ic_home_assistant) {
    Row(
        Modifier.fillMaxWidth().padding(2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconFromResource(resourceId = iconRes)
        AppText(text = "  $text", fontSize = 24.sp)
    }
}

/** A spinner with a short text: the loading state of a card. */
@Composable
internal fun HaLoading(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        AppText(text = text, fontSize = 14.sp)
    }
}

/**
 * Plain text in the card: the empty state, or a good ([error] false) or bad result. One short line only: the first
 * sentence shows, the rest of [text] and [more] sit behind an (i) tap ([HaText.split]).
 */
@Composable
internal fun HaNote(text: String, error: Boolean = false, more: String? = null) {
    val (line, rest) = HaText.split(text)
    val hidden = listOfNotNull(rest, more?.takeIf { it.isNotBlank() }).joinToString(" ").takeIf { it.isNotBlank() }
    var open by remember(text) { mutableStateOf(false) }
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(text = text, fontSize = 14.sp, color = color, modifier = Modifier.weight(1f, fill = false))
            if (hidden != null) {
                IconButton(onClick = { open = !open }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = if (open) "Less" else "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (open && hidden != null) AppText(text = hidden, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The short-text rule of the screens: one short line shows, the rest goes behind an (i) tap. Pure. */
object HaText {
    /** Longest line a note shows before the (i). */
    const val MAX_LINE = 80

    /** [text] as its first sentence and the rest (null for none). */
    fun split(text: String): Pair<String, String?> {
        val m = Regex("""[.!?] (?=[A-Z])""").find(text) ?: return text to null
        val head = text.substring(0, m.range.first + 1)
        return head to text.substring(m.range.last + 1).trim().takeIf { it.isNotEmpty() }
    }

    /** True when [line] keeps the rule: one sentence of at most [MAX_LINE] characters. */
    fun isShort(line: String): Boolean = line.length <= MAX_LINE && split(line).second == null
}

/** "Details": the technical text (codes, last answer), hidden until asked for. Nothing when [text] is blank. */
@Composable
internal fun HaDetails(text: String, extra: (@Composable () -> Unit)? = null) {
    if (text.isBlank() && extra == null) return
    var open by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide details" else "Details") }
        if (open) {
            extra?.invoke()
            if (text.isNotBlank()) AppText(text = text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Opens [url] in the browser; false when no app can. */
internal fun openInBrowser(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}
