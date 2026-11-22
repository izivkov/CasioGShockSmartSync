package org.avmedia.gshockGoogleSync.webhook

import AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import AppSwitch
import org.avmedia.gshockGoogleSync.R

private const val SHOWN = 5

/** Card 3: what goes to Home Assistant, one switch per group, with the values of the last update. */
@Composable
internal fun HaSentCard(sent: HaSentUi, saved: Boolean, onGroup: (HaGroup, Boolean) -> Unit) {
    HaCard("What is sent", R.drawable.ic_watch_later_black_24dp) {
        if (!saved) {
            HaNote("Set up the connection first.")
            return@HaCard
        }
        if (sent.sending) HaLoading("Sending...")
        if (sent.failed) HaNote("The last update did not go through. Nothing is lost: it is tried again.", error = true)
        sent.groups.forEach { g ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                AppText(text = g.group.title, fontWeight = FontWeight.SemiBold)
                AppSwitch(checked = g.on, onCheckedChange = { onGroup(g.group, it) })
            }
            Group(g)
        }
    }
}

@Composable
private fun Group(g: HaGroupUi) {
    when {
        !g.on -> HaNote("Switched off. Nothing of this is sent.")
        g.rows.isEmpty() -> HaNote("No values yet. They appear after the watch connects.")
        else -> Column {
            g.rows.take(SHOWN).forEach { (name, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppText(text = name, fontSize = 14.sp)
                    AppText(text = value, fontSize = 14.sp)
                }
            }
            if (g.rows.size > SHOWN) HaNote("and ${g.rows.size - SHOWN} more")
        }
    }
}
