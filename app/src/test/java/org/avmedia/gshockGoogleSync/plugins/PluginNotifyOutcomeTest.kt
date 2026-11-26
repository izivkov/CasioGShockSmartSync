package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.casio.NotificationSender.Outcome
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.PluginResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The Home Assistant (MQTT) plugin logged "notify done" and the owner got nothing: the host said
 * done as soon as the text was queued. A plugin's notify now gives what happened to the text.
 */
class PluginNotifyOutcomeTest {
    private fun reason(r: WriteResult) = (r as PluginResult.Failed).reason

    @Test fun aWatchThatCannotShowThemFailsAndNothingIsSent() = runBlocking {
        var sent = false
        val r = PluginHub.deliverNotification(shows = false) { sent = true; Outcome.SENT }
        assertEquals(FailReason.NOT_SUPPORTED, reason(r))
        assertFalse(sent)
    }

    @Test fun aTextThatDidNotReachTheWatchIsNotDone() = runBlocking {
        assertEquals(FailReason.INTERNAL, reason(PluginHub.deliverNotification(shows = true) { Outcome.WRITE_FAILED }))
        assertEquals(FailReason.BUSY, reason(PluginHub.deliverNotification(shows = true) { Outcome.HELD_TOO_LONG }))
        assertEquals(FailReason.INVALID, reason(PluginHub.deliverNotification(shows = true) { Outcome.EMPTY }))
    }

    @Test fun onlyASentTextIsDone() = runBlocking {
        assertEquals(CommandScheduler.DONE, PluginHub.deliverNotification(shows = true) { Outcome.SENT })
    }
}
