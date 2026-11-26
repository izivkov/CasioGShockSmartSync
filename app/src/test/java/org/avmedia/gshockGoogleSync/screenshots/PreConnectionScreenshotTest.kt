package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.session.EntryReason
import org.avmedia.gshockGoogleSync.session.SessionPhase
import org.avmedia.gshockGoogleSync.session.SessionState
import org.avmedia.gshockGoogleSync.session.WatchSnapshot
import org.avmedia.gshockGoogleSync.ui.others.PreConnectionViewModel.DeviceItem
import org.avmedia.gshockGoogleSync.ui.others.WatchCards
import org.avmedia.gshockGoogleSync.ui.others.WatchOptions
import org.junit.Test

/**
 * The watch list cards: a connected watch, a waiting one (Keep connected on, not here now), a not connected one, and
 * a tapped watch the list waits for; the Watch options sheet with Keep connected on and off.
 */
class PreConnectionScreenshotTest : ScreenshotTest() {
    private val h5600 = DeviceItem("GBX-H5600", "AA:BB:CC:DD:EE:01", isLastUsed = true, isAlwaysConnected = true)
    private val dw = DeviceItem("DW-H5600", "AA:BB:CC:DD:EE:03", isLastUsed = false, isAlwaysConnected = true)
    private val gw = DeviceItem("GW-B5600", "AA:BB:CC:DD:EE:02", isLastUsed = false)

    private fun connected(address: String) =
        SessionState(phase = SessionPhase.Ready(EntryReason.BACKGROUND), watch = WatchSnapshot(address = address))

    // The list is half the screen wide on the first page.
    @Composable
    private fun Cards(session: SessionState, holder: String?, waiting: String? = null) =
        WatchCards(listOf(h5600, dw, gw), session, holder, waiting, onSelect = {}, onOptions = {}, onCancelWaiting = {},
            modifier = Modifier.width(206.dp).padding(4.dp))

    @Test fun connectedLight() = snap("watch_list_cards_connected", false) { Cards(connected(h5600.address), h5600.address) }
    @Test fun connectedDark() = snap("watch_list_cards_connected", true) { Cards(connected(h5600.address), h5600.address) }
    @Test fun waitingLight() = snap("watch_list_cards_waiting", false) { Cards(SessionState(), h5600.address) }
    @Test fun waitingDark() = snap("watch_list_cards_waiting", true) { Cards(SessionState(), h5600.address) }
    @Test fun tappedLight() = snap("watch_list_cards_tapped", false) { Cards(SessionState(), h5600.address, waiting = gw.address) }
    @Test fun tappedDark() = snap("watch_list_cards_tapped", true) { Cards(SessionState(), h5600.address, waiting = gw.address) }

    @Composable
    private fun Sheets() = Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        WatchOptions(h5600, keepOn = true, otherHolderName = null, onKeepConnected = {}, onForget = {})
        WatchOptions(dw, keepOn = false, otherHolderName = h5600.name, onKeepConnected = {}, onForget = {})
        WatchOptions(gw, keepOn = false, otherHolderName = h5600.name, onKeepConnected = {}, onForget = {})
    }

    @Test fun optionsLight() = snap("watch_options_sheet", false) { Sheets() }
    @Test fun optionsDark() = snap("watch_options_sheet", true) { Sheets() }
}
