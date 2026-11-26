package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import org.avmedia.gshockGoogleSync.LinkBarLayout
import org.avmedia.gshockGoogleSync.session.LinkState
import org.avmedia.gshockGoogleSync.ui.time.TimeTitleRow
import org.avmedia.gshockGoogleSync.ui.watchhealth.ValueRow
import org.junit.Test

/** The Time title with the Watches button, under the reconnecting bar; Settings' Home Assistant entry; a long value. */
class WatchListEntryScreenshotTest : ScreenshotTest() {
    @Composable private fun Top(link: LinkState) = LinkBarLayout(link) {
        Column {
            TimeTitleRow {}
            org.avmedia.gshockGoogleSync.plugins.PluginCard(org.avmedia.gshockGoogleSync.plugins.FakePlugin(), "Connected", on = true, onSwitch = {}, onOpen = {})
            ValueRow("Target zone", "Zone 2, 120 to 150 bpm and a long text that needs several lines", false, note = "Read only")
        }
    }

    @Test fun reconnectingLight() = snap("time_title_reconnecting", false) { Top(LinkState.RECONNECTING) }
    @Test fun reconnectingDark() = snap("time_title_reconnecting", true) { Top(LinkState.RECONNECTING) }
    @Test fun linkUpLight() = snap("time_title_link_up", false) { Top(LinkState.UP) }
}
