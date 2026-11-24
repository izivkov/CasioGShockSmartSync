package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.ui.settings.PhoneNotificationsContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchHealthLinks
import org.junit.Test

/** The top of Settings on a DW-H5600 family watch: the two entries that open Watch face and Watch health settings. */
class SettingsEntriesScreenshotTest : ScreenshotTest() {
    @Composable private fun Top() = Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        WatchHealthLinks({}, {})
        PhoneNotificationsContent(org.avmedia.gshockGoogleSync.notifications.PhoneNotificationMode.ALL, 0, {}, {})
    }

    @Test fun topLight() = snap("settings_entries", false) { Top() }
    @Test fun topDark() = snap("settings_entries", true) { Top() }
}
