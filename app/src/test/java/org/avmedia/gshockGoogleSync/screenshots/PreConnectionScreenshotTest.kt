package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.ui.others.PairedDeviceList
import org.avmedia.gshockGoogleSync.ui.others.PreConnectionViewModel.DeviceItem
import org.junit.Test

/** First page watch list: the "Open when the watch connects" switch off, on, and waiting. */
class PreConnectionScreenshotTest : ScreenshotTest() {
    private fun item(on: Boolean) = listOf(
        DeviceItem("DW-H5600", "AA:BB:CC:DD:EE:01", isLastUsed = true, isAlwaysConnected = true, keepConnected = on),
        DeviceItem("GW-B5600", "AA:BB:CC:DD:EE:02", isLastUsed = false)
    )

    @Composable
    private fun List(on: Boolean, waiting: String? = null) =
        PairedDeviceList(item(on), onSelect = {}, onDisassociate = {}, waitingAddress = waiting,
            modifier = Modifier.fillMaxWidth().padding(16.dp))

    @Test fun switchOffLight() = snap("first_page_open_off", false) { List(false) }
    @Test fun switchOffDark() = snap("first_page_open_off", true) { List(false) }
    @Test fun switchOnLight() = snap("first_page_open_on", false) { List(true) }
    @Test fun switchOnDark() = snap("first_page_open_on", true) { List(true) }
    @Test fun waitingLight() = snap("first_page_open_waiting", false) { List(false, "AA:BB:CC:DD:EE:01") }
}
