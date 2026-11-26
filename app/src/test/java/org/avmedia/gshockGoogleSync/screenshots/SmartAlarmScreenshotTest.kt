package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.avmedia.gshockGoogleSync.ui.alarms.SmartAlarmCard
import org.avmedia.gshockGoogleSync.ui.alarms.SmartAlarmEvents
import org.avmedia.gshockGoogleSync.ui.alarms.SmartAlarmUi
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockapi.io.SmartAlarmCodec
import org.junit.Test

/** The top of the Alarms tab on an H5600: the smart alarm card above the normal alarms; and before the watch answered. */
class SmartAlarmScreenshotTest : ScreenshotTest() {
    @Composable private fun Top(ui: SmartAlarmUi) = Column(Modifier) {
        ScreenTitle("Alarms", Modifier)
        SmartAlarmCard(ui, SmartAlarmEvents())
    }

    private val on = SmartAlarmUi(alarm = SmartAlarmCodec.Setting(6, 30, true, false, 30))

    @Test fun onLight() = snap("alarms_smart_alarm", false) { Top(on) }
    @Test fun onDark() = snap("alarms_smart_alarm", true) { Top(on) }
    @Test fun offLight() = snap("alarms_smart_alarm_off", false) { Top(SmartAlarmUi(alarm = SmartAlarmCodec.Setting(7, 5, false, false, 15))) }
    @Test fun noAnswerLight() = snap("alarms_smart_alarm_no_answer", false) { Top(SmartAlarmUi(message = "The watch did not answer.")) }
}
