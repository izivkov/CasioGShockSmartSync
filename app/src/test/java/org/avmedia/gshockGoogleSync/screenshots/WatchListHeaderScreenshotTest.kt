package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.ui.others.WatchListHeader
import org.junit.Test

/** Top left of the watch list: the first page (App log only) and from the connected screens with a watch (Back and App log). */
class WatchListHeaderScreenshotTest : ScreenshotTest() {
    @Test fun firstPageLight() = snap("watch_list_header_first_page", false) { WatchListHeader(null, {}, Modifier.padding(top = 24.dp, start = 16.dp)) }
    @Test fun firstPageDark() = snap("watch_list_header_first_page", true) { WatchListHeader(null, {}, Modifier.padding(top = 24.dp, start = 16.dp)) }
    @Test fun connectedLight() = snap("watch_list_header_connected", false) { WatchListHeader({}, {}, Modifier.padding(top = 24.dp, start = 16.dp)) }
    @Test fun connectedDark() = snap("watch_list_header_connected", true) { WatchListHeader({}, {}, Modifier.padding(top = 24.dp, start = 16.dp)) }
}
