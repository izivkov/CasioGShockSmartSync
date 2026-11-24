package org.avmedia.gshockGoogleSync.ui.alarms

import androidx.compose.runtime.Composable
import org.avmedia.gshockapi.io.SmartAlarmCodec

/** Whether the Alarms tab shows the smart alarm card for [watchName]. */
fun smartAlarmShownFor(watchName: String): Boolean = false

data class SmartAlarmUi(val working: Boolean = false, val alarm: SmartAlarmCodec.Setting? = null, val message: String? = null)

class SmartAlarmEvents(
    val onTime: (Int, Int) -> Unit = { _, _ -> },
    val onOn: (Boolean) -> Unit = {},
    val onSnooze: (Boolean) -> Unit = {},
    val onWindow: (Int) -> Unit = {},
)

@Composable
fun SmartAlarmCard(ui: SmartAlarmUi, events: SmartAlarmEvents) {
}
