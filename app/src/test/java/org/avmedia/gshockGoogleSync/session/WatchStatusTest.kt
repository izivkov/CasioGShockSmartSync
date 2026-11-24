package org.avmedia.gshockGoogleSync.session

import org.junit.Assert.assertEquals
import org.junit.Test

/** The status on a watch card follows the session: connected, waiting (kept connected, not here now), not connected. */
class WatchStatusTest {
    private val a = "AA:BB:CC:DD:EE:01"
    private val b = "AA:BB:CC:DD:EE:02"

    private fun ready(address: String, link: LinkState = LinkState.UP) =
        SessionState(phase = SessionPhase.Ready(EntryReason.BACKGROUND), watch = WatchSnapshot(address = address), link = link)

    @Test fun connectedNow() = assertEquals(WatchStatus.CONNECTED, WatchStatus.of(a, ready(a.lowercase()), keepHolder = null))

    @Test fun keptConnectedButAwayIsWaiting() {
        assertEquals(WatchStatus.WAITING, WatchStatus.of(a, ready(a, LinkState.RECONNECTING), keepHolder = a))
        assertEquals(WatchStatus.WAITING, WatchStatus.of(a, ready(a, LinkState.LOST), keepHolder = a))
        assertEquals(WatchStatus.WAITING, WatchStatus.of(a, SessionState(), keepHolder = a))
        assertEquals(WatchStatus.WAITING, WatchStatus.of(a, ready(b), keepHolder = a))
    }

    @Test fun otherwiseNotConnected() {
        assertEquals(WatchStatus.NOT_CONNECTED, WatchStatus.of(a, SessionState(), keepHolder = b))
        assertEquals(WatchStatus.NOT_CONNECTED, WatchStatus.of(a, ready(b), keepHolder = null))
        assertEquals(WatchStatus.NOT_CONNECTED, WatchStatus.of(a, ready(a, LinkState.LOST), keepHolder = null))
        assertEquals(
            WatchStatus.NOT_CONNECTED,
            WatchStatus.of(a, SessionState(phase = SessionPhase.Closed(), watch = WatchSnapshot(address = a)), keepHolder = null),
        )
    }
}
