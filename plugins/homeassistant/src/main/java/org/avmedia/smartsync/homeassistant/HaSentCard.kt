package org.avmedia.smartsync.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val SHOWN = 5

/**
 * Card 3: what goes to Home Assistant. Everything the plugin maps is sent; one switch keeps health data out (its sensors
 * are disabled in Home Assistant while it is off). Below, the values of the last update per group.
 */
@Composable
internal fun HaSentCard(sent: HaSentUi, saved: Boolean, onHealth: (Boolean) -> Unit) {
    HaCard("What is sent", R.drawable.ic_mdi_upload) {
        if (!saved) {
            HaNote("Set up the connection first.")
            return@HaCard
        }
        if (sent.sending) HaLoading("Sending...")
        if (sent.failed) HaNote("The last update did not go through. Nothing is lost: it is tried again.", error = true)
        val healthOn = sent.groups.firstOrNull { it.group == HaGroup.HEALTH }?.on ?: true
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            AppText(text = "Share health data with Home Assistant", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            AppSwitch(checked = healthOn, onCheckedChange = onHealth)
        }
        HaNote("Watch, settings and the button are always sent.", more = "Off: the health sensors are disabled in Home Assistant and no health value is sent.")
        sent.groups.forEach { g ->
            AppText(text = g.group.title, fontWeight = FontWeight.SemiBold)
            Group(g)
        }
    }
}

@Composable
private fun Group(g: HaGroupUi) {
    when {
        !g.on -> HaNote("Not shared.")
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
