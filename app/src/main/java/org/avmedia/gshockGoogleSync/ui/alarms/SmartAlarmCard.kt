package org.avmedia.gshockGoogleSync.ui.alarms

import AppSwitch
import AppText
import AppTextExtraLarge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.session.WatchModelOverride
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.watchhealth.ChoiceDialog
import org.avmedia.gshockGoogleSync.ui.watchhealth.TimeDialog
import org.avmedia.gshockGoogleSync.ui.watchhealth.hhmm
import org.avmedia.gshockapi.io.SmartAlarmCodec

/** The Alarms tab shows the smart alarm card only for the H5600 family (the watches with the 0x50 smart alarm). */
fun smartAlarmShownFor(watchName: String): Boolean = WatchModelOverride.isH5600Family(watchName)

data class SmartAlarmUi(val working: Boolean = false, val alarm: SmartAlarmCodec.Setting? = null, val message: String? = null)

class SmartAlarmEvents(
    val onTime: (Int, Int) -> Unit = { _, _ -> },
    val onOn: (Boolean) -> Unit = {},
    val onSnooze: (Boolean) -> Unit = {},
    val onWindow: (Int) -> Unit = {},
)

/** The smart alarm card of the Alarms tab, bound to its view model (reads 0x50 when it is first shown). */
@Composable
fun SmartAlarmSection(viewModel: SmartAlarmViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    SmartAlarmCard(ui, SmartAlarmEvents(viewModel::setTime, viewModel::setOn, viewModel::setSnooze, viewModel::setWindow))
}

/** Time, on, snooze and wake window of the smart alarm, above the normal alarms. Dimmed until the watch answered. */
@Composable
fun SmartAlarmCard(ui: SmartAlarmUi, events: SmartAlarmEvents) {
    var timeOpen by remember { mutableStateOf(false) }
    var windowOpen by remember { mutableStateOf(false) }
    val alarm = ui.alarm
    val can = alarm != null && !ui.working
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().alpha(if (can) 1f else 0.5f).padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppTextExtraLarge(
                    text = alarm?.let { hhmm(it.hour to it.minute) } ?: "--:--",
                    modifier = Modifier.padding(4.dp).then(if (can) Modifier.clickable { timeOpen = true } else Modifier),
                )
                Spacer(Modifier.width(8.dp))
                AppText(text = "Smart alarm", modifier = Modifier.weight(1f))
                AppSwitch(checked = alarm?.on ?: false, onCheckedChange = events.onOn, enabled = can)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f).then(if (can) Modifier.clickable { windowOpen = true } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(text = "Wake window", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    AppText(text = alarm?.let { "${it.windowMinutes} min" } ?: "-", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
                AppText(text = "Snooze", fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                AppSwitch(checked = alarm?.snooze ?: false, onCheckedChange = events.onSnooze, enabled = can && alarm?.on == true)
            }
            val text = if (ui.working) "Talking to the watch..." else ui.message
            if (text != null) AppText(text = text, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        }
    }
    if (timeOpen && alarm != null) TimeDialog(alarm.hour, alarm.minute, { h, m -> timeOpen = false; events.onTime(h, m) }, { timeOpen = false })
    if (windowOpen && alarm != null) {
        val windows = SmartAlarmCodec.WINDOWS
        ChoiceDialog("Wake window", windows.map { "$it min" }, windows.indexOf(alarm.windowMinutes), { windowOpen = false; events.onWindow(windows[it]) }, { windowOpen = false })
    }
}
